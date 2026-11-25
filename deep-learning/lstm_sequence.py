"""
Deep Learning Model 2: PyTorch LSTM Sequence Model for Temporal Fraud Detection.
Processes chronological sequences (sorted by Time) using proper weighted BCE loss.
"""

import json
import os
import sys
import numpy as np
import pandas as pd
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
import torch
import torch.nn as nn
from torch.utils.data import DataLoader, TensorDataset
from sklearn.metrics import average_precision_score, roc_auc_score, confusion_matrix

BASE_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, BASE_DIR)
from src.preprocess import load_data, chronological_split_and_scale

DEVICE = torch.device("cuda" if torch.cuda.is_available() else "cpu")


class FraudLSTM(nn.Module):
    def __init__(self, input_dim=30, hidden_dim=32, num_layers=1):
        super(FraudLSTM, self).__init__()
        self.lstm = nn.LSTM(
            input_size=input_dim,
            hidden_size=hidden_dim,
            num_layers=num_layers,
            batch_first=True,
            dropout=0.0,
        )
        self.classifier = nn.Sequential(
            nn.Dropout(0.2),
            nn.Linear(hidden_dim, 16),
            nn.ReLU(),
            nn.Linear(16, 1),
        )

    def forward_logits(self, x):
        lstm_out, _ = self.lstm(x)
        last_timestep = lstm_out[:, -1, :]
        return self.classifier(last_timestep).squeeze(1)

    def forward(self, x):
        return torch.sigmoid(self.forward_logits(x))


def create_sequences(X, y, seq_len=5):
    """Create vectorized chronological temporal sliding-window sequences."""
    windows = np.lib.stride_tricks.sliding_window_view(X, window_shape=seq_len, axis=0)
    # windows has shape (num_samples, num_features, seq_len) -> transpose to (num_samples, seq_len, num_features)
    X_seq = np.ascontiguousarray(windows.transpose(0, 2, 1), dtype=np.float32)
    y_seq = np.ascontiguousarray(y[seq_len - 1:], dtype=np.float32)
    return X_seq, y_seq


def set_seed(seed=42):
    np.random.seed(seed)
    torch.manual_seed(seed)


def train_lstm(seq_len=5, epochs=12, batch_size=512, lr=1.5e-3, cost_fn=250.0, cost_fp=15.0):
    set_seed(42)
    csv_path = os.path.join(BASE_DIR, "data", "creditcard.csv")
    models_dir = os.path.join(BASE_DIR, "models")
    reports_dir = os.path.join(BASE_DIR, "reports")
    figures_dir = os.path.join(reports_dir, "figures")
    os.makedirs(models_dir, exist_ok=True)
    os.makedirs(figures_dir, exist_ok=True)

    df = load_data(csv_path)
    # Use strict chronological split so sequence windows follow real transaction time
    X_train, X_test, y_train, y_test, _ = chronological_split_and_scale(df, test_size=0.2)

    # Order features consistently V1..V28, Scaled_Time, Scaled_Amount
    col_order = [f"V{i}" for i in range(1, 29)] + ["Scaled_Time", "Scaled_Amount"]
    X_tr_arr = X_train[col_order].values
    X_te_arr = X_test[col_order].values

    print(f"[+] Creating chronological sliding sequences (seq_len={seq_len}) across ALL rows...")
    X_tr_seq, y_tr_seq = create_sequences(X_tr_arr, y_train.values, seq_len=seq_len)
    X_te_seq, y_te_seq = create_sequences(X_te_arr, y_test.values, seq_len=seq_len)

    # Validation split from tail of training sequences (no future test leakage)
    val_cut = int(len(X_tr_seq) * 0.9)
    X_tr_sub, y_tr_sub = X_tr_seq[:val_cut], y_tr_seq[:val_cut]
    X_val_sub, y_val_sub = X_tr_seq[val_cut:], y_tr_seq[val_cut:]

    train_loader = DataLoader(
        TensorDataset(torch.tensor(X_tr_sub), torch.tensor(y_tr_sub)),
        batch_size=batch_size,
        shuffle=True,
    )

    model = FraudLSTM(input_dim=30, hidden_dim=32).to(DEVICE)

    # Moderated positive weight with BCEWithLogitsLoss so threshold 0.5 stays calibrated while capturing rare frauds
    raw_ratio = (len(y_tr_sub) - y_tr_sub.sum()) / max(1.0, y_tr_sub.sum())
    effective_pos_weight = float(min(30.0, np.sqrt(raw_ratio)))
    criterion = nn.BCEWithLogitsLoss(pos_weight=torch.tensor([effective_pos_weight]).to(DEVICE))
    optimizer = torch.optim.AdamW(model.parameters(), lr=lr, weight_decay=1e-4)

    print(
        f"[+] Training FraudLSTM on {len(X_tr_sub):,} sequences "
        f"({int(y_tr_sub.sum())} positives, pos_weight={effective_pos_weight:.2f})..."
    )

    train_losses, val_losses = [], []
    best_val_pr = -1.0
    ckpt_path = os.path.join(models_dir, "lstm_model.pt")

    for epoch in range(1, epochs + 1):
        model.train()
        total_loss = 0.0
        for x_b, y_b in train_loader:
            x_b, y_b = x_b.to(DEVICE), y_b.to(DEVICE)
            optimizer.zero_grad()
            logits = model.forward_logits(x_b)
            loss = criterion(logits, y_b)
            loss.backward()
            optimizer.step()
            total_loss += loss.item() * len(x_b)

        epoch_loss = total_loss / len(X_tr_sub)
        train_losses.append(epoch_loss)

        model.eval()
        with torch.no_grad():
            x_v = torch.tensor(X_val_sub).to(DEVICE)
            y_v = torch.tensor(y_val_sub).to(DEVICE)
            val_logits = model.forward_logits(x_v)
            v_loss = float(criterion(val_logits, y_v).item())
            val_probs = torch.sigmoid(val_logits).cpu().numpy()
            val_pr = float(average_precision_score(y_val_sub, val_probs))
        val_losses.append(v_loss)

        if val_pr > best_val_pr:
            best_val_pr = val_pr
            torch.save(model.state_dict(), ckpt_path)

        print(f"Epoch [{epoch:02d}/{epochs:02d}] Train Loss: {epoch_loss:.5f} | Val Loss: {v_loss:.5f} | Val PR-AUC: {val_pr:.4f}")

    # Load best validation checkpoint
    model.load_state_dict(torch.load(ckpt_path, map_location=DEVICE, weights_only=True))
    model.eval()

    # Tune optimal F1/cost threshold on validation sequence set (never on test set)
    with torch.no_grad():
        val_probs = model(torch.tensor(X_val_sub).to(DEVICE)).cpu().numpy()
    best_thr, best_val_loss = 0.5, float("inf")
    for cand_thr in [0.5, 0.6, 0.7, 0.8, 0.9]:
        p_val = (val_probs >= cand_thr).astype(int)
        _, fp_v, fn_v, _ = confusion_matrix(y_val_sub, p_val).ravel()
        cost_v = fn_v * cost_fn + fp_v * cost_fp
        if cost_v < best_val_loss:
            best_val_loss = cost_v
            best_thr = cand_thr

    # Evaluate strictly on full chronological test holdout sequences
    with torch.no_grad():
        test_preds_batches = []
        for start in range(0, len(X_te_seq), 2048):
            xb = torch.tensor(X_te_seq[start : start + 2048]).to(DEVICE)
            test_preds_batches.append(model(xb).cpu().numpy())
        test_probs = np.concatenate(test_preds_batches)

    pr_auc = float(average_precision_score(y_te_seq, test_probs))
    roc_auc = float(roc_auc_score(y_te_seq, test_probs))
    preds = (test_probs >= best_thr).astype(int)
    tn, fp, fn, tp = confusion_matrix(y_te_seq, preds).ravel()
    precision = float(tp / (tp + fp)) if (tp + fp) else 0.0
    recall = float(tp / (tp + fn)) if (tp + fn) else 0.0
    f1 = float(2 * precision * recall / (precision + recall)) if (precision + recall) else 0.0
    loss_usd = float(fn * cost_fn + fp * cost_fp)

    metrics = {
        "Model": "LSTM Sequence Model (Chronological PyTorch)",
        "PR-AUC": round(pr_auc, 4),
        "ROC-AUC": round(roc_auc, 4),
        "Recall": round(recall, 4),
        "Precision": round(precision, 4),
        "F1-Score": round(f1, 4),
        "TP": int(tp),
        "FP": int(fp),
        "TN": int(tn),
        "FN": int(fn),
        "Financial Loss ($)": round(loss_usd, 2),
        "Test_Samples": int(len(y_te_seq)),
        "Threshold": round(best_thr, 2),
    }

    print(f"\n[+] LSTM Full Chronological Test ({len(y_te_seq):,}, frauds={int(y_te_seq.sum())}):")
    print(f"    PR-AUC: {pr_auc:.4f} | ROC-AUC: {roc_auc:.4f} | Thr={best_thr}")
    print(f"    TP={tp}, FP={fp}, FN={fn} | P={precision:.4f}, R={recall:.4f}, F1={f1:.4f}, Loss=${loss_usd:,.2f}")

    # Plot training & validation loss curve
    fig, ax = plt.subplots(figsize=(7, 4))
    ax.plot(range(1, epochs + 1), train_losses, marker="o", color="#1b6c80", linewidth=2, label="Train BCE Loss")
    ax.plot(range(1, epochs + 1), val_losses, marker="s", color="#a86f0c", linewidth=2, linestyle="--", label="Val BCE Loss")
    ax.set_title("LSTM Sequence Model Weighted BCE Loss (Chronological Split)")
    ax.set_xlabel("Epochs")
    ax.set_ylabel("Weighted BCEWithLogits Loss")
    ax.legend()
    ax.grid(True, linestyle="--", alpha=0.4)
    plt.tight_layout()
    fig_path = os.path.join(figures_dir, "lstm_training.png")
    fig.savefig(fig_path, dpi=200)
    plt.close(fig)

    # Save metadata & update dl_benchmark_results.csv
    meta_path = os.path.join(models_dir, "dl_metadata.json")
    meta = {}
    if os.path.exists(meta_path):
        with open(meta_path, "r") as f:
            meta = json.load(f)
    meta["lstm"] = {
        "seq_len": seq_len,
        "val_tuned_threshold": best_thr,
        "pos_weight": round(effective_pos_weight, 4),
        "split": "chronological_80_20",
        "metrics": metrics,
    }
    with open(meta_path, "w") as f:
        json.dump(meta, f, indent=2)

    dl_csv = os.path.join(reports_dir, "dl_benchmark_results.csv")
    if os.path.exists(dl_csv):
        dl_df = pd.read_csv(dl_csv)
        dl_df = dl_df[~dl_df["Model"].str.contains("LSTM", na=False)]
        dl_df = pd.concat([dl_df, pd.DataFrame([metrics])], ignore_index=True)
    else:
        dl_df = pd.DataFrame([metrics])
    dl_df.to_csv(dl_csv, index=False)

    return model, metrics


if __name__ == "__main__":
    train_lstm(epochs=12)
