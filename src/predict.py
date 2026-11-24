"""
Inference script to score new incoming credit card transactions.
Loads serialized XGBoost model and fitted RobustScaler pipeline.
"""

import os
import sys
import joblib
import pandas as pd
import numpy as np

BASE_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MODELS_DIR = os.path.join(BASE_DIR, "models")
MODEL_PATH = os.path.join(MODELS_DIR, "xgboost_model.pkl")
SCALERS_PATH = os.path.join(MODELS_DIR, "scalers.pkl")

def load_inference_artifacts():
    """Load model and scalers for real-time inference."""
    if not os.path.exists(MODEL_PATH) or not os.path.exists(SCALERS_PATH):
        raise FileNotFoundError("Model or scalers not found. Run 'python main.py --model xgboost' first.")
    model = joblib.load(MODEL_PATH)
    scalers = joblib.load(SCALERS_PATH)
    return model, scalers

def predict_transaction(transaction_dict_or_df, threshold=0.5):
    """
    Score a transaction or batch of transactions.
    
    Expected features: Time, V1-V28, Amount
    Returns: DataFrame with Fraud_Probability and Fraud_Prediction
    """
    model, scalers = load_inference_artifacts()
    
    if isinstance(transaction_dict_or_df, dict):
        df = pd.DataFrame([transaction_dict_or_df])
    else:
        df = transaction_dict_or_df.copy()
        
    # Drop target if present
    if "Class" in df.columns:
        df = df.drop(columns=["Class"])
        
    # Apply fitted scalers without refitting
    if "Time" in df.columns and scalers.get("scaler_time"):
        df["Scaled_Time"] = scalers["scaler_time"].transform(df[["Time"]])
        df = df.drop(columns=["Time"])
        
    if "Amount" in df.columns and scalers.get("scaler_amount"):
        df["Scaled_Amount"] = scalers["scaler_amount"].transform(df[["Amount"]])
        df = df.drop(columns=["Amount"])
        
    # Ensure column order matches training feature set
    probs = model.predict_proba(df)[:, 1]
    preds = (probs >= threshold).astype(int)
    
    results = pd.DataFrame({
        "Fraud_Probability": np.round(probs, 4),
        "Fraud_Prediction": preds,
        "Risk_Status": ["FLAGGED HIGH RISK" if p == 1 else "APPROVED" for p in preds]
    })
    
    return results

if __name__ == "__main__":
    print("[*] Running sanity check on sample transactions from test set...")
    from src.preprocess import load_data
    csv_path = os.path.join(BASE_DIR, "data", "creditcard.csv")
    test_df = load_data(csv_path).sample(5, random_state=101)
    
    preds = predict_transaction(test_df)
    combined = pd.concat([test_df[["Amount", "Class"]].reset_index(drop=True), preds], axis=1)
    print("\nInference Results:")
    print(combined.to_string(index=False))
