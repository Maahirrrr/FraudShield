"""
Deep Learning Model 2: PyTorch LSTM Sequence Model for Temporal Fraud Detection.
Evaluates bursts of transactions across time to detect card-testing attacks.
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

sys.path.append(os.path.abspath(".."))
from src.preprocess import load_data, split_and_scale

DEVICE = torch.device("cuda" if torch.cuda.is_available() else "cpu")

class FraudLSTM(nn.Module):
    def __init__(self, input_dim=30, hidden_dim=32, num_layers=1):
        super(FraudLSTM, self).__init__()
        self.lstm = nn.LSTM(
            input_size=input_dim,
            hidden_size=hidden_dim,
            num_layers=num_layers,
            batch_first=True,
            dropout=0.0
        )
        self.classifier = nn.Sequential(
            nn.Dropout(0.2),
            nn.Linear(hidden_dim, 16),
            nn.ReLU(),
            nn.Linear(16, 1),
            nn.Sigmoid()
        )

    def forward(self, x):
        # x shape: (batch_size, seq_len, input_dim)
        lstm_out, _ = self.lstm(x)
        last_timestep = lstm_out[:, -1, :]  # Take hidden state from last timestep
        out = self.classifier(last_timestep)
        return out.squeeze(1)

def create_sequences(X, y, seq_len=5):
    """Create temporal sequences of transactions."""
    num_samples = len(X) - seq_len + 1
    X_seq = np.zeros((num_samples, seq_len, X.shape[1]), dtype=np.float32)
    y_seq = np.zeros(num_samples, dtype=np.float32)
    
    for i in range(num_samples):
        X_seq[i] = X[i : i + seq_len]
        y_seq[i] = y[i + seq_len - 1] # label of latest transaction
        
    return X_seq, y_seq

def train_lstm(seq_len=5, epochs=5, batch_size=256, lr=1e-3):
    csv_path = os.path.abspath("../data/creditcard.csv")
    df = load_data(csv_path)
    X_train, X_test, y_train, y_test, _ = split_and_scale(df, test_size=0.2, random_state=42)

    print(f"[+] Creating temporal sequences (seq_len={seq_len})...")
    # Subsample slightly to accelerate CPU training for 2-week B.Tech prototype
    train_subset_size = 50000
    X_tr_seq, y_tr_seq = create_sequences(X_train.values[:train_subset_size], y_train.values[:train_subset_size], seq_len)
    X_te_seq, y_te_seq = create_sequences(X_test.values[:10000], y_test.values[:10000], seq_len)

    train_loader = DataLoader(
        TensorDataset(torch.tensor(X_tr_seq, dtype=torch.float32), torch.tensor(y_tr_seq, dtype=torch.float32)),
        batch_size=batch_size, shuffle=True
    )

    model = FraudLSTM(input_dim=X_train.shape[1], hidden_dim=32).to(DEVICE)
    # Binary cross entropy with positive class weight for imbalance
    pos_weight = torch.tensor([(len(y_tr_seq) - y_tr_seq.sum()) / max(1, y_tr_seq.sum())]).to(DEVICE)
    criterion = nn.BCELoss()
    optimizer = torch.optim.AdamW(model.parameters(), lr=lr, weight_decay=1e-4)

    print(f"[+] Training LSTM on {len(X_tr_seq):,} temporal transaction sequences...")
    losses = []
    for epoch in range(1, epochs + 1):
        model.train()
        total_loss = 0.0
        for x_b, y_b in train_loader:
            x_b, y_b = x_b.to(DEVICE), y_b.to(DEVICE)
            optimizer.zero_grad()
            preds = model(x_b)
            loss = criterion(preds, y_b)
            loss.backward()
            optimizer.step()
            total_loss += loss.item() * len(x_b)

        epoch_loss = total_loss / len(X_tr_seq)
        losses.append(epoch_loss)
        print(f"Epoch [{epoch:02d}/{epochs:02d}] BCE Loss: {epoch_loss:.5f}")

    # Save model checkpoint
    os.makedirs("../models", exist_ok=True)
    torch.save(model.state_dict(), "../models/lstm_model.pt")

    # Evaluate on test sequence
    model.eval()
    with torch.no_grad():
        test_preds = model(torch.tensor(X_te_seq, dtype=torch.float32).to(DEVICE)).cpu().numpy()

    pr_auc = average_precision_score(y_te_seq, test_preds)
    roc_auc = roc_auc_score(y_te_seq, test_preds)
    print(f"[+] LSTM Test PR-AUC: {pr_auc:.4f} | ROC-AUC: {roc_auc:.4f}")

    # Plot loss curve
    os.makedirs("../reports/figures", exist_ok=True)
    fig, ax = plt.subplots(figsize=(7, 4))
    ax.plot(range(1, epochs + 1), losses, marker="o", color="#8e44ad", linewidth=2)
    ax.set_title("LSTM Sequence Model BCE Training Loss")
    ax.set_xlabel("Epochs")
    ax.set_ylabel("Weighted BCE Loss")
    plt.tight_layout()
    fig_path = "../reports/figures/lstm_training.png"
    fig.savefig(fig_path, dpi=300)
    plt.close(fig)
    print(f"[+] Saved LSTM curve: {fig_path}")

    return model

if __name__ == "__main__":
    train_lstm(epochs=5)
