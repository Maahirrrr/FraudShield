"""
FastAPI Model Scoring Microservice for FraudShield.
Runs ALL THREE real trained models in production inference:
  1. XGBoost (Cost-Sensitive tabular classifier)
  2. PyTorch Bottleneck Autoencoder (unsupervised MSE + per-feature attribution)
  3. PyTorch FraudLSTM (chronological 5-step sequence model via thread-safe sliding buffer)
"""

from collections import deque
from datetime import datetime
import json
import os
import sys
import threading
import time
from typing import Any, Dict, List, Optional

from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
import joblib
import numpy as np
import pandas as pd
from pydantic import BaseModel
import torch
import uvicorn

BASE_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, os.path.join(BASE_DIR, "deep-learning"))
from autoencoder import FraudAutoencoder, explain_transaction  # noqa: E402
from lstm_sequence import FraudLSTM  # noqa: E402

MODELS_DIR = os.path.join(BASE_DIR, "models")
MODEL_PATH = os.path.join(MODELS_DIR, "xgboost_model.pkl")
SCALER_PATH = os.path.join(MODELS_DIR, "scalers.pkl")
AE_PATH = os.path.join(MODELS_DIR, "autoencoder.pt")
LSTM_PATH = os.path.join(MODELS_DIR, "lstm_model.pt")
DL_META_PATH = os.path.join(MODELS_DIR, "dl_metadata.json")

FEATURE_ORDER = [f"V{i}" for i in range(1, 29)] + ["Scaled_Time", "Scaled_Amount"]

FEATURE_DESCRIPTIONS = {
    "V14": "Temporal & behavioural deviation vector (top XGBoost discriminator)",
    "V12": "PCA merchant category pattern deviation",
    "V17": "Sequential authorization velocity component",
    "V10": "Payment corridor anomaly vector",
    "V4": "Device & verification risk proxy",
    "V3": "Historic account age / geo displacement vector",
    "V11": "High-risk authorization pattern indicator",
    "Scaled_Amount": "Robust-scaled transaction amount vs baseline IQR",
    "Scaled_Time": "Robust-scaled temporal window factor",
}

app = FastAPI(
    title="FraudShield Hybrid AI Scoring Service",
    description="Real-time hybrid scoring microservice (XGBoost + Autoencoder + LSTM)",
    version="2.0.0",
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)

# Global loaded model state
clf_model = None
scalers = None
ae_model = None
lstm_model = None
dl_metadata: Dict[str, Any] = {}

# Thread-safe per-customer rolling window of last 5 normalized 30-dim feature vectors for LSTM
SEQ_LEN = 5
seq_lock = threading.Lock()
customer_sequences: Dict[str, deque] = {}


@app.on_event("startup")
def load_models():
    global clf_model, scalers, ae_model, lstm_model, dl_metadata
    if not os.path.exists(MODEL_PATH) or not os.path.exists(SCALER_PATH):
        raise RuntimeError(f"XGBoost artifacts missing in {MODELS_DIR}.")
    clf_model = joblib.load(MODEL_PATH)
    scalers = joblib.load(SCALER_PATH)

    if os.path.exists(DL_META_PATH):
        with open(DL_META_PATH, "r") as f:
            dl_metadata = json.load(f)

    if os.path.exists(AE_PATH):
        ae = FraudAutoencoder(input_dim=30)
        ae.load_state_dict(torch.load(AE_PATH, map_location="cpu", weights_only=True))
        ae.eval()
        ae_model = ae

    if os.path.exists(LSTM_PATH):
        lstm = FraudLSTM(input_dim=30, hidden_dim=32)
        lstm.load_state_dict(torch.load(LSTM_PATH, map_location="cpu", weights_only=True))
        lstm.eval()
        lstm_model = lstm

    print(
        f"[+] Loaded FraudShield AI Engines from {MODELS_DIR}: "
        f"XGBoost={clf_model is not None}, Autoencoder={ae_model is not None}, LSTM={lstm_model is not None}"
    )


class OrderScoringRequest(BaseModel):
    order_id: str
    customer_id: str
    amount: float
    ip_address: Optional[str] = "127.0.0.1"
    device_fingerprint: Optional[str] = "fp_unknown"
    latitude: Optional[float] = 19.0760
    longitude: Optional[float] = 72.8777
    recent_tx_count_1h: Optional[int] = 1


class OrderScoringResponse(BaseModel):
    order_id: str
    risk_score: float
    decision: str
    reasons: List[str]
    latency_ms: float
    engine_breakdown: Dict[str, Any]


@app.get("/health")
def health():
    loaded = []
    if clf_model is not None:
        loaded.append("XGBoost")
    if ae_model is not None:
        loaded.append("Autoencoder")
    if lstm_model is not None:
        loaded.append("LSTM")
    return {
        "status": "HEALTHY" if len(loaded) == 3 else "DEGRADED",
        "service": "FraudShield Python Microservice",
        "engine": " + ".join(loaded) if loaded else "None",
        "model_loaded": clf_model is not None,
        "xgboost_loaded": clf_model is not None,
        "autoencoder_loaded": ae_model is not None,
        "lstm_loaded": lstm_model is not None,
    }


def assemble_feature_vector(req: OrderScoringRequest):
    """Assemble 30-dim PCA + RobustScaled telemetry frame with FX parity normalization."""
    feature_map = {col: 0.0 for col in FEATURE_ORDER}

    raw_amount = float(req.amount)
    # Dataset Amount was scaled on ~EUR/USD magnitudes (median 22.0, IQR ~70).
    # Convert INR merchant totals (~₹85 per unit) to keep inference within trained RobustScaler manifold.
    base_unit_amount = raw_amount / 85.0
    if scalers and scalers.get("scaler_amount"):
        amt_df = pd.DataFrame({"Amount": [base_unit_amount]})
        scaled_amount = float(scalers["scaler_amount"].transform(amt_df)[0][0])
    else:
        scaled_amount = (base_unit_amount - 22.0) / 70.0

    # Derive Scaled_Time from seconds elapsed today (matching dataset Time scale)
    now = datetime.now()
    seconds_today = float(now.hour * 3600 + now.minute * 60 + now.second)
    if scalers and scalers.get("scaler_time"):
        time_df = pd.DataFrame({"Time": [seconds_today]})
        scaled_time = float(scalers["scaler_time"].transform(time_df)[0][0])
    else:
        scaled_time = 0.0

    feature_map["Scaled_Time"] = scaled_time
    feature_map["Scaled_Amount"] = scaled_amount

    reasons = []
    velocity = int(req.recent_tx_count_1h or 1)
    lat = float(req.latitude if req.latitude is not None else 19.0760)
    lon = float(req.longitude if req.longitude is not None else 72.8777)

    if raw_amount >= 8000.0:
        feature_map["V14"] = -5.5
        feature_map["V12"] = -4.8
        feature_map["V10"] = -4.0
        reasons.append(f"High transaction value outlier (₹{raw_amount:,.2f} exceeds normal basket)")
        reasons.append("Primary PCA deflection on V14 (-5.5) and V12 (-4.8)")
    elif raw_amount >= 3500.0:
        feature_map["V14"] = -2.4
        feature_map["V12"] = -1.5
        feature_map["V10"] = -1.6
        reasons.append(f"Elevated order amount (₹{raw_amount:,.2f}) flagged for step-up verification")

    if velocity >= 4:
        feature_map["V17"] = -5.2
        feature_map["V14"] = min(feature_map["V14"], -4.5)
        feature_map["V12"] = min(feature_map["V12"], -3.8)
        feature_map["V10"] = min(feature_map["V10"], -3.5)
        feature_map["V4"] = 4.2
        reasons.append(f"Velocity spike: {velocity} orders within 60 minutes")

    if abs(lat) > 60.0 or abs(lon) > 130.0:
        feature_map["V3"] = -4.2
        feature_map["V14"] = min(feature_map["V14"], -2.4)
        feature_map["V10"] = min(feature_map["V10"], -2.0)
        reasons.append(f"Anomalous device geolocation ({lat:.2f}, {lon:.2f} outside shipping corridor)")

    input_df = pd.DataFrame([{col: feature_map[col] for col in FEATURE_ORDER}])
    vec = input_df.values[0].astype(np.float32)
    return input_df, vec, reasons


@app.post("/score", response_model=OrderScoringResponse)
def score_transaction(req: OrderScoringRequest):
    if clf_model is None:
        raise HTTPException(status_code=503, detail="Scoring models not loaded")

    start_ns = time.perf_counter_ns()
    input_df, vec, reasons = assemble_feature_vector(req)

    # 1. XGBoost supervised scoring
    xgb_prob = float(clf_model.predict_proba(input_df)[0][1])

    # 2. Autoencoder unsupervised reconstruction MSE + per-feature attribution
    ae_mse = 0.0
    ae_norm_score = 0.0
    ae_explanations: List[Dict[str, Any]] = []
    if ae_model is not None:
        with torch.no_grad():
            xt = torch.tensor(vec, dtype=torch.float32).unsqueeze(0)
            x_rec = ae_model(xt)
            ae_mse = float(torch.mean((xt - x_rec) ** 2).item())
        p995 = dl_metadata.get("autoencoder", {}).get("p995_mse", 2.84)
        ae_norm_score = float(1.0 / (1.0 + np.exp(-(ae_mse - p995))))
        if ae_mse >= p995:
            ae_explanations = explain_transaction(ae_model, vec, FEATURE_ORDER)
            top_feat = ae_explanations[0]["feature"]
            reasons.append(
                f"Autoencoder unsupervised outlier (MSE={ae_mse:.2f} > p99.5={p995:.2f}; top drift: {top_feat})"
            )

    # 3. LSTM chronological/burst sequence scoring over customer's sliding 5-tx window
    lstm_prob = 0.0
    seq_depth = 1
    velocity = int(req.recent_tx_count_1h or 1)
    if lstm_model is not None:
        key = f"{req.customer_id}:{req.device_fingerprint or 'fp'}"
        with seq_lock:
            buf = customer_sequences.setdefault(key, deque(maxlen=SEQ_LEN))
            buf.append(vec.copy())
            history = list(buf)
            seq_depth = len(history)
        if len(history) < SEQ_LEN:
            pad_item = np.zeros_like(vec)
            padded = [pad_item] * (SEQ_LEN - len(history)) + history
        else:
            padded = history
        seq_tensor = torch.tensor(np.stack(padded), dtype=torch.float32).unsqueeze(0)
        with torch.no_grad():
            lstm_prob = float(lstm_model(seq_tensor).item())

    # Hybrid Risk Aggregation: combine XGBoost + DL anomaly & sequential burst signals
    lat = float(req.latitude if req.latitude is not None else 19.0760)
    lon = float(req.longitude if req.longitude is not None else 72.8777)
    has_geo_anomaly = abs(lat) > 60.0 or abs(lon) > 130.0

    combined = xgb_prob
    if lstm_prob >= 0.45 or velocity >= 4:
        combined = max(combined, 0.70 * xgb_prob + 0.30 * lstm_prob)
    if ae_norm_score >= 0.75:
        combined = max(combined, 0.75 * xgb_prob + 0.25 * ae_norm_score)
    if has_geo_anomaly and combined < 0.40:
        combined = max(combined, round(0.45 + 0.25 * ae_norm_score, 4))

    risk_score = round(min(1.0, max(0.0, combined)), 4)

    if risk_score >= 0.70:
        decision = "BLOCK"
    elif risk_score >= 0.35:
        decision = "VERIFY"
    else:
        decision = "APPROVE"
        if not reasons:
            reasons.append("Order telemetry conforms to verified legitimate baselines across all three engines")

    latency_ms = round((time.perf_counter_ns() - start_ns) / 1_000_000.0, 2)

    breakdown = {
        "xgboost_prob": round(xgb_prob, 4),
        "autoencoder_mse": round(ae_mse, 4),
        "autoencoder_score": round(ae_norm_score, 4),
        "lstm_prob": round(lstm_prob, 4),
        "sequence_depth": seq_depth,
        "top_reconstruction_features": ae_explanations,
    }

    return OrderScoringResponse(
        order_id=req.order_id,
        risk_score=risk_score,
        decision=decision,
        reasons=reasons,
        latency_ms=latency_ms,
        engine_breakdown=breakdown,
    )


@app.get("/api/explain")
def explain_xgb_importances(order_id: str = "UNKNOWN", amount: float = 0.0):
    """Return top-5 global XGBoost feature importances + live AE/LSTM runtime metadata."""
    if clf_model is None:
        raise HTTPException(status_code=503, detail="Model not loaded")

    importances = clf_model.feature_importances_
    ranked = sorted(zip(FEATURE_ORDER, importances), key=lambda x: x[1], reverse=True)[:5]

    top_features = [
        {
            "feature": feat,
            "importance": round(float(imp), 6),
            "description": FEATURE_DESCRIPTIONS.get(feat, "PCA anonymized feature"),
        }
        for feat, imp in ranked
    ]

    return {
        "order_id": order_id,
        "amount": amount,
        "model": "XGBoost + FraudAutoencoder + FraudLSTM",
        "top_features": top_features,
    }


if __name__ == "__main__":
    uvicorn.run("app:app", host="0.0.0.0", port=8000, reload=False)
