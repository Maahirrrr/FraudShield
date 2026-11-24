"""
Standalone Threshold Sensitivity Analysis for FraudShield.
Evaluates cost matrix ($250 FN, $15 FP) and Precision/Recall across varying classification thresholds.
Generates tabular metrics and saves threshold_sensitivity.png figure.
"""

import os
import joblib
import numpy as np
import pandas as pd
import matplotlib.pyplot as plt
from sklearn.metrics import precision_recall_fscore_support

BASE_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DATA_PATH = os.path.join(BASE_DIR, "data", "creditcard.csv")
MODEL_PATH = os.path.join(BASE_DIR, "models", "xgboost_model.pkl")
SCALER_PATH = os.path.join(BASE_DIR, "models", "scalers.pkl")
OUTPUT_FIG = os.path.join(BASE_DIR, "reports", "figures", "threshold_sensitivity.png")

def run_threshold_analysis():
    print("[+] Loading model, scalers, and dataset...")
    clf = joblib.load(MODEL_PATH)
    scalers = joblib.load(SCALER_PATH)
    
    df = pd.read_csv(DATA_PATH)
    
    # Scale features
    df['Scaled_Amount'] = scalers['scaler_amount'].transform(df[['Amount']])
    df['Scaled_Time'] = scalers['scaler_time'].transform(df[['Time']])
    
    feature_cols = [f"V{i}" for i in range(1, 29)] + ['Scaled_Time', 'Scaled_Amount']
    X = df[feature_cols]
    y = df['Class'].values
    
    # Stratified 80/20 test split (matching training)
    from sklearn.model_selection import train_test_split
    _, X_test, _, y_test = train_test_split(X, y, test_size=0.2, random_state=42, stratify=y)
    
    y_probs = clf.predict_proba(X_test)[:, 1]
    
    thresholds = [0.30, 0.40, 0.50, 0.60, 0.70, 0.80]
    records = []
    
    cost_fn = 250.0 # Financial cost of missed fraud
    cost_fp = 15.0  # Operational cost of false dispute
    
    print("\n" + "="*75)
    print(f"{'Threshold':<10} {'Precision':<12} {'Recall':<10} {'F1-Score':<10} {'TP':<6} {'FP':<6} {'FN':<6} {'Loss ($)':<10}")
    print("="*75)
    
    for t in thresholds:
        preds = (y_probs >= t).astype(int)
        p, r, f1, _ = precision_recall_fscore_support(y_test, preds, average='binary', zero_division=0)
        
        tp = int(np.sum((preds == 1) & (y_test == 1)))
        fp = int(np.sum((preds == 1) & (y_test == 0)))
        fn = int(np.sum((preds == 0) & (y_test == 1)))
        
        loss = (fn * cost_fn) + (fp * cost_fp)
        
        records.append({
            'threshold': t,
            'precision': p,
            'recall': r,
            'f1': f1,
            'tp': tp,
            'fp': fp,
            'fn': fn,
            'loss': loss
        })
        
        print(f"{t:<10.2f} {p:<12.4f} {r:<10.4f} {f1:<10.4f} {tp:<6} {fp:<6} {fn:<6} ${loss:<9,.2f}")
    print("="*75)
    
    # Plotting
    res_df = pd.DataFrame(records)
    fig, ax1 = plt.subplots(figsize=(8, 5))
    
    color = 'tab:blue'
    ax1.set_xlabel('Classification Threshold', fontsize=11, fontweight='bold')
    ax1.set_ylabel('Metric Value', color=color, fontsize=11, fontweight='bold')
    ax1.plot(res_df['threshold'], res_df['precision'], marker='o', label='Precision', color='tab:blue', linewidth=2)
    ax1.plot(res_df['threshold'], res_df['recall'], marker='s', label='Recall', color='tab:orange', linewidth=2)
    ax1.plot(res_df['threshold'], res_df['f1'], marker='^', label='F1-Score', color='tab:green', linewidth=2)
    ax1.tick_params(axis='y', labelcolor=color)
    ax1.set_ylim(0, 1.05)
    ax1.axvline(x=0.70, color='red', linestyle='--', label='Production Threshold (0.70)')
    ax1.grid(True, linestyle=':', alpha=0.6)
    
    ax2 = ax1.twinx()
    color = 'tab:red'
    ax2.set_ylabel('Total Financial Loss ($)', color=color, fontsize=11, fontweight='bold')
    ax2.plot(res_df['threshold'], res_df['loss'], marker='x', label='Financial Loss ($)', color='tab:red', linestyle='-.', linewidth=2)
    ax2.tick_params(axis='y', labelcolor=color)
    
    plt.title('FraudShield: Threshold Sensitivity vs Financial Loss Curve', fontsize=12, fontweight='bold')
    fig.tight_layout()
    
    os.makedirs(os.path.dirname(OUTPUT_FIG), exist_ok=True)
    plt.savefig(OUTPUT_FIG, dpi=150)
    print(f"[+] Saved sensitivity plot to: {OUTPUT_FIG}")

if __name__ == "__main__":
    run_threshold_analysis()
