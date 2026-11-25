"""
Deep Learning Model 1: PyTorch Autoencoder for Unsupervised Anomaly Detection.
Trained strictly on legitimate transactions (Class 0).
Persists calibrated percentile thresholds and empirical test metrics to disk.
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
from src.preprocess import load_data, split_and_scale

DEVICE = torch.device("cuda" if torch.cuda.is_available() else "cpu")


class FraudAutoencoder(nn.Module):
    def __init__(self, input_dim=30):
        super(FraudAutoencoder, self).__init__()
        self.encoder = nn.Sequential(
            nn.Linear(input_dim, 20),
            nn.BatchNorm1d(20),
            nn.LeakyReLU(0.1),
            nn.Dropout(0.1),
            nn.Linear(20, 12),
            nn.BatchNorm1d(12),
            nn.LeakyReLU(0.1),
            nn.Linear(12, 6),
        )
        self.decoder = nn.Sequential(
            nn.Linear(6, 12),
            nn.BatchNorm1d(12),
            nn.LeakyReLU(0.1),
            nn.Dropout(0.1),
            nn.Linear(12, 20),
            nn.BatchNorm1d(20),
            nn.LeakyReLU(0.1),
            nn.Linear(20, input_dim),
        )

    def forward(self, x):
        encoded = self.encoder(x)
        decoded = self.decoder(encoded)
        return decoded


def set_seed(seed=42):
    np.random.seed(seed)
    torch.manual_seed(seed)


def train_autoencoder(epochs=20, batch_size=256, lr=1e-3, cost_fn=250.0, cost_fp=15.0):
    set_seed(42)
    csv_path = os.path.join(BASE_DIR, "data", "creditcard.csv")
    models_dir = os.path.join(BASE_DIR, "models")
    reports_dir = os.path.join(BASE_DIR, "reports")
    figures_dir = os.path.join(reports_dir, "figures")
    os.makedirs(models_dir, exist_ok=True)
    os.makedirs(figures_dir, exist_ok=True)

    df = load_data(csv_path)
    X_train, X_test, y_train, y_test, _ = split_and_scale(df, test_size=0.2, random_state=42)

    # Filter training set to legitimate transactions only (y == 0)
    X_train_legit = X_train[y_train == 0].values
    X_val_legit = X_train_legit[:10000]
    X_tr_legit = X_train_legit[10000:]

    train_tensor = torch.tensor(X_tr_legit, dtype=torch.float32)
    val_tensor = torch.tensor(X_val_legit, dtype=torch.float32)
    test_tensor = torch.tensor(X_test.values, dtype=torch.float32)

    train_loader = DataLoader(TensorDataset(train_tensor), batch_size=batch_size, shuffle=True)
    val_loader = DataLoader(TensorDataset(val_tensor), batch_size=batch_size, shuffle=False)

    model = FraudAutoencoder(input_dim=X_train.shape[1]).to(DEVICE)
    criterion = nn.MSELoss()
    optimizer = torch.optim.AdamW(model.parameters(), lr=lr, weight_decay=1e-5)

    print(f"[+] Training Autoencoder on {len(X_tr_legit):,} legitimate transactions for {epochs} epochs...")
    train_losses, val_losses = [], []
    best_loss = float("inf")
    ckpt_path = os.path.join(models_dir, "autoencoder.pt")

    for epoch in range(1, epochs + 1):
        model.train()
        total_loss = 0.0
        for (x_batch,) in train_loader:
            x_batch = x_batch.to(DEVICE)
            optimizer.zero_grad()
            reconstructed = model(x_batch)
            loss = criterion(reconstructed, x_batch)
            loss.backward()
            optimizer.step()
            total_loss += loss.item() * len(x_batch)

        train_loss = total_loss / len(X_tr_legit)
        train_losses.append(train_loss)

        model.eval()
        val_loss = 0.0
        with torch.no_grad():
            for (x_b,) in val_loader:
                x_b = x_b.to(DEVICE)
                rec = model(x_b)
                val_loss += criterion(rec, x_b).item() * len(x_b)
        val_loss /= len(X_val_legit)
        val_losses.append(val_loss)

        if val_loss < best_loss:
            best_loss = val_loss
            torch.save(model.state_dict(), ckpt_path)

        print(f"Epoch [{epoch:02d}/{epochs:02d}] Train MSE: {train_loss:.5f} | Val MSE: {val_loss:.5f}")

    # Load best checkpoint
    model.load_state_dict(torch.load(ckpt_path, map_location=DEVICE, weights_only=True))
    model.eval()

    # Plot learning curve
    fig, ax = plt.subplots(figsize=(7, 4))
    ax.plot(range(1, epochs + 1), train_losses, label="Train Loss (MSE)", linewidth=2, color="#1b6c80")
    ax.plot(range(1, epochs + 1), val_losses, label="Val Loss (MSE)", linewidth=2, color="#a86f0c", linestyle="--")
    ax.set_title("Autoencoder Training & Validation Reconstruction Loss")
    ax.set_xlabel("Epochs")
    ax.set_ylabel("Reconstruction MSE")
    ax.legend()
    ax.grid(True, linestyle="--", alpha=0.4)
    plt.tight_layout()
    fig_path = os.path.join(figures_dir, "autoencoder_training.png")
    fig.savefig(fig_path, dpi=200)
    plt.close(fig)

    # Calibrate MSE percentiles on validation legitimate set
    with torch.no_grad():
        val_rec = model(val_tensor.to(DEVICE))
        val_mse = torch.mean((val_tensor.to(DEVICE) - val_rec) ** 2, dim=1).cpu().numpy()

        test_rec = model(test_tensor.to(DEVICE))
        rec_errors = torch.mean((test_tensor.to(DEVICE) - test_rec) ** 2, dim=1).cpu().numpy()

    p95 = float(np.percentile(val_mse, 95))
    p99 = float(np.percentile(val_mse, 99))
    p995 = float(np.percentile(val_mse, 99.5))

    pr_auc = float(average_precision_score(y_test, rec_errors))
    roc_auc = float(roc_auc_score(y_test, rec_errors))

    # Evaluate classification operating point at p99.5 validation threshold (minimal false alarm rate)
    preds = (rec_errors >= p995).astype(int)
    tn, fp, fn, tp = confusion_matrix(y_test, preds).ravel()
    precision = float(tp / (tp + fp)) if (tp + fp) else 0.0
    recall = float(tp / (tp + fn)) if (tp + fn) else 0.0
    f1 = float(2 * precision * recall / (precision + recall)) if (precision + recall) else 0.0
    loss_usd = float(fn * cost_fn + fp * cost_fp)

    metrics = {
        "Model": "Bottleneck Autoencoder (Unsupervised @ p99.5)",
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
        "Test_Samples": int(len(y_test)),
        "Threshold": round(p995, 4),
    }

    print(f"\n[+] Autoencoder Full Test ({len(y_test):,}) -> PR-AUC: {pr_auc:.4f} | ROC-AUC: {roc_auc:.4f}")
    print(f"    @ p99.5 ({p995:.4f}): TP={tp}, FP={fp}, FN={fn} | P={precision:.4f}, R={recall:.4f}, F1={f1:.4f}, Loss=${loss_usd:,.2f}")

    # Persist metadata & update dl_benchmark_results.csv
    meta_path = os.path.join(models_dir, "dl_metadata.json")
    meta = {}
    if os.path.exists(meta_path):
        with open(meta_path, "r") as f:
            meta = json.load(f)
    meta["autoencoder"] = {
        "p95_mse": round(p95, 6),
        "p99_mse": round(p99, 6),
        "p995_mse": round(p995, 6),
        "pr_auc": metrics["PR-AUC"],
        "roc_auc": metrics["ROC-AUC"],
        "metrics": metrics,
    }
    with open(meta_path, "w") as f:
        json.dump(meta, f, indent=2)

    dl_csv = os.path.join(reports_dir, "dl_benchmark_results.csv")
    if os.path.exists(dl_csv):
        dl_df = pd.read_csv(dl_csv)
        dl_df = dl_df[~dl_df["Model"].str.contains("Autoencoder", na=False)]
        dl_df = pd.concat([dl_df, pd.DataFrame([metrics])], ignore_index=True)
    else:
        dl_df = pd.DataFrame([metrics])
    dl_df.to_csv(dl_csv, index=False)

    return model, metrics


def explain_transaction(model, x_sample, feature_names):
    """Identify features contributing most to reconstruction error."""
    model.eval()
    with torch.no_grad():
        x_tensor = torch.tensor(x_sample, dtype=torch.float32).unsqueeze(0).to(DEVICE)
        x_rec = model(x_tensor)
        sq_diff = ((x_tensor - x_rec) ** 2).squeeze(0).cpu().numpy()

    top_idx = np.argsort(sq_diff)[::-1][:3]
    return [
        {
            "feature": feature_names[i],
            "squared_error": round(float(sq_diff[i]), 4),
            "message": f"Reconstruction anomaly in '{feature_names[i]}' (sq_err={sq_diff[i]:.2f})",
        }
        for i in top_idx
    ]


if __name__ == "__main__":
    train_autoencoder(epochs=20)
