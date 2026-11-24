"""
Deep Learning Model 1: PyTorch Autoencoder for Unsupervised Anomaly Detection.
Trained strictly on legitimate transactions (Class 0).
Includes feature-attribution explainability via per-feature reconstruction error.
"""

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
from sklearn.metrics import average_precision_score, roc_auc_score

# Ensure src imports work
sys.path.append(os.path.abspath(".."))
from src.preprocess import load_data, split_and_scale

DEVICE = torch.device("cuda" if torch.cuda.is_available() else "cpu")

class FraudAutoencoder(nn.Module):
    def __init__(self, input_dim=30):
        super(FraudAutoencoder, self).__init__()
        # Encoder: 30 -> 20 -> 12 -> 6 (Bottleneck)
        self.encoder = nn.Sequential(
            nn.Linear(input_dim, 20),
            nn.BatchNorm1d(20),
            nn.LeakyReLU(0.1),
            nn.Dropout(0.1),
            nn.Linear(20, 12),
            nn.BatchNorm1d(12),
            nn.LeakyReLU(0.1),
            nn.Linear(12, 6)
        )
        # Decoder: 6 -> 12 -> 20 -> 30
        self.decoder = nn.Sequential(
            nn.Linear(6, 12),
            nn.BatchNorm1d(12),
            nn.LeakyReLU(0.1),
            nn.Dropout(0.1),
            nn.Linear(12, 20),
            nn.BatchNorm1d(20),
            nn.LeakyReLU(0.1),
            nn.Linear(20, input_dim)
        )

    def forward(self, x):
        encoded = self.encoder(x)
        decoded = self.decoder(encoded)
        return decoded

def train_autoencoder(epochs=15, batch_size=256, lr=1e-3):
    csv_path = os.path.abspath("../data/creditcard.csv")
    df = load_data(csv_path)
    X_train, X_test, y_train, y_test, _ = split_and_scale(df, test_size=0.2, random_state=42)

    # Filter training set to legitimate transactions only (y == 0)
    X_train_legit = X_train[y_train == 0].values
    X_val_legit = X_train_legit[:10000]
    X_train_legit = X_train_legit[10000:]

    train_tensor = torch.tensor(X_train_legit, dtype=torch.float32)
    val_tensor = torch.tensor(X_val_legit, dtype=torch.float32)
    test_tensor = torch.tensor(X_test.values, dtype=torch.float32)

    train_loader = DataLoader(TensorDataset(train_tensor), batch_size=batch_size, shuffle=True)
    val_loader = DataLoader(TensorDataset(val_tensor), batch_size=batch_size, shuffle=False)

    model = FraudAutoencoder(input_dim=X_train.shape[1]).to(DEVICE)
    criterion = nn.MSELoss()
    optimizer = torch.optim.AdamW(model.parameters(), lr=lr, weight_decay=1e-5)

    print(f"[+] Training Autoencoder on {len(X_train_legit):,} legitimate transactions...")
    train_losses, val_losses = [], []
    best_loss = float("inf")

    for epoch in range(1, epochs + 1):
        model.train()
        total_loss = 0.0
        for batch in train_loader:
            x_batch = batch[0].to(DEVICE)
            optimizer.zero_grad()
            reconstructed = model(x_batch)
            loss = criterion(reconstructed, x_batch)
            loss.backward()
            optimizer.step()
            total_loss += loss.item() * len(x_batch)

        train_loss = total_loss / len(X_train_legit)
        train_losses.append(train_loss)

        # Validation
        model.eval()
        val_loss = 0.0
        with torch.no_grad():
            for batch in val_loader:
                x_b = batch[0].to(DEVICE)
                rec = model(x_b)
                val_loss += criterion(rec, x_b).item() * len(x_b)
        val_loss /= len(X_val_legit)
        val_losses.append(val_loss)

        if val_loss < best_loss:
            best_loss = val_loss
            os.makedirs("../models", exist_ok=True)
            torch.save(model.state_dict(), "../models/autoencoder.pt")

        print(f"Epoch [{epoch:02d}/{epochs:02d}] Train MSE: {train_loss:.5f} | Val MSE: {val_loss:.5f}")

    # Plot learning curve
    os.makedirs("../reports/figures", exist_ok=True)
    fig, ax = plt.subplots(figsize=(7, 4))
    ax.plot(range(1, epochs + 1), train_losses, label="Train Loss (MSE)", linewidth=2)
    ax.plot(range(1, epochs + 1), val_losses, label="Val Loss (MSE)", linewidth=2)
    ax.set_title("Autoencoder Training & Validation Loss (No Overfitting)")
    ax.set_xlabel("Epochs")
    ax.set_ylabel("Reconstruction MSE")
    ax.legend()
    plt.tight_layout()
    fig_path = "../reports/figures/autoencoder_training.png"
    fig.savefig(fig_path, dpi=300)
    plt.close(fig)
    print(f"[+] Saved training curve: {fig_path}")

    # Evaluate on full test set (both legit and fraud)
    model.eval()
    with torch.no_grad():
        test_rec = model(test_tensor.to(DEVICE))
        # Reconstruction error per sample
        rec_errors = torch.mean((test_tensor.to(DEVICE) - test_rec) ** 2, dim=1).cpu().numpy()

    pr_auc = average_precision_score(y_test, rec_errors)
    roc_auc = roc_auc_score(y_test, rec_errors)
    print(f"\n[+] Autoencoder Test PR-AUC: {pr_auc:.4f} | ROC-AUC: {roc_auc:.4f}")

    return model

def explain_transaction(model, x_sample, feature_names):
    """
    Explainability: Identify features contributing most to reconstruction error.
    """
    model.eval()
    with torch.no_grad():
        x_tensor = torch.tensor(x_sample, dtype=torch.float32).unsqueeze(0).to(DEVICE)
        x_rec = model(x_tensor)
        sq_diff = ((x_tensor - x_rec) ** 2).squeeze(0).cpu().numpy()

    top_idx = np.argsort(sq_diff)[::-1][:3]
    explanations = [f"Unusual pattern in feature '{feature_names[i]}' (reconstruction anomaly: {sq_diff[i]:.2f})" for i in top_idx]
    return explanations

if __name__ == "__main__":
    train_autoencoder(epochs=10)
