"""
Comprehensive evaluation metrics, diagnostic curves, and cost-benefit financial analysis.
"""

import os
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
import seaborn as sns
import numpy as np
import pandas as pd
from sklearn.metrics import (
    average_precision_score,
    roc_auc_score,
    precision_recall_curve,
    roc_curve,
    confusion_matrix,
    classification_report
)

def evaluate_model(model, X_test, y_test, model_name="Model", cost_fn=250.0, cost_fp=15.0):
    """
    Evaluate trained model on test data:
    - PR-AUC (Average Precision)
    - ROC-AUC
    - Recall, Precision, F1
    - Expected Financial Cost: (FN * cost_fn) + (FP * cost_fp)
    """
    if hasattr(model, "predict_proba"):
        y_prob = model.predict_proba(X_test)[:, 1]
    elif hasattr(model, "decision_function"):
        y_prob = model.decision_function(X_test)
        # Normalize to [0, 1] for PR-AUC if needed
        y_prob = (y_prob - y_prob.min()) / (y_prob.max() - y_prob.min() + 1e-8)
    else:
        # For IsolationForest
        raw_pred = model.predict(X_test)  # -1 for anomaly, 1 for normal
        y_prob = np.where(raw_pred == -1, 1, 0)
        
    y_pred = (y_prob >= 0.5).astype(int) if hasattr(model, "predict_proba") else model.predict(X_test)
    if set(np.unique(y_pred)).issubset({-1, 1}):
        y_pred = np.where(y_pred == -1, 1, 0)
        
    cm = confusion_matrix(y_test, y_pred)
    tn, fp, fn, tp = cm.ravel()
    
    pr_auc = average_precision_score(y_test, y_prob)
    roc_auc = roc_auc_score(y_test, y_prob)
    
    precision = tp / (tp + fp) if (tp + fp) > 0 else 0.0
    recall = tp / (tp + fn) if (tp + fn) > 0 else 0.0
    f1 = 2 * (precision * recall) / (precision + recall) if (precision + recall) > 0 else 0.0
    
    total_cost = (fn * cost_fn) + (fp * cost_fp)
    
    metrics = {
        "Model": model_name,
        "PR-AUC": round(pr_auc, 4),
        "ROC-AUC": round(roc_auc, 4),
        "Recall": round(recall, 4),
        "Precision": round(precision, 4),
        "F1-Score": round(f1, 4),
        "TP": int(tp),
        "FP": int(fp),
        "TN": int(tn),
        "FN": int(fn),
        "Financial Loss ($)": round(total_cost, 2)
    }
    
    return metrics, cm, (y_test, y_prob)

def plot_confusion_matrix(cm, model_name, output_dir="reports/figures"):
    """Plot and save confusion matrix heatmap."""
    os.makedirs(output_dir, exist_ok=True)
    fig, ax = plt.subplots(figsize=(5, 4))
    sns.heatmap(cm, annot=True, fmt="d", cmap="Blues", cbar=False, ax=ax,
                xticklabels=["Legitimate", "Fraud"],
                yticklabels=["Legitimate", "Fraud"])
    ax.set_title(f"Confusion Matrix: {model_name}")
    ax.set_xlabel("Predicted Label")
    ax.set_ylabel("True Label")
    plt.tight_layout()
    filename = f"cm_{model_name.replace(' ', '_').lower()}.png"
    filepath = os.path.join(output_dir, filename)
    fig.savefig(filepath, dpi=300)
    plt.close(fig)
    return filepath

def plot_benchmark_curves(models_curves, output_dir="reports/figures"):
    """Plot overlaid Precision-Recall and ROC curves for all models."""
    os.makedirs(output_dir, exist_ok=True)
    
    # 1. Precision-Recall Curves
    fig_pr, ax_pr = plt.subplots(figsize=(8, 6))
    for name, (y_test, y_prob) in models_curves.items():
        precision, recall, _ = precision_recall_curve(y_test, y_prob)
        ap = average_precision_score(y_test, y_prob)
        ax_pr.plot(recall, precision, label=f"{name} (PR-AUC = {ap:.3f})", linewidth=2)
        
    ax_pr.set_title("Precision-Recall Curves (Primary Imbalanced Metric)")
    ax_pr.set_xlabel("Recall (Sensitivity)")
    ax_pr.set_ylabel("Precision")
    ax_pr.legend(loc="lower left")
    ax_pr.grid(True, linestyle="--", alpha=0.6)
    plt.tight_layout()
    pr_path = os.path.join(output_dir, "benchmark_pr_curves.png")
    fig_pr.savefig(pr_path, dpi=300)
    plt.close(fig_pr)
    
    # 2. ROC Curves
    fig_roc, ax_roc = plt.subplots(figsize=(8, 6))
    for name, (y_test, y_prob) in models_curves.items():
        fpr, tpr, _ = roc_curve(y_test, y_prob)
        auc = roc_auc_score(y_test, y_prob)
        ax_roc.plot(fpr, tpr, label=f"{name} (ROC-AUC = {auc:.3f})", linewidth=2)
        
    ax_roc.plot([0, 1], [0, 1], "k--", alpha=0.5, label="Random Guess")
    ax_roc.set_title("Receiver Operating Characteristic (ROC) Curves")
    ax_roc.set_xlabel("False Positive Rate")
    ax_roc.set_ylabel("True Positive Rate")
    ax_roc.legend(loc="lower right")
    ax_roc.grid(True, linestyle="--", alpha=0.6)
    plt.tight_layout()
    roc_path = os.path.join(output_dir, "benchmark_roc_curves.png")
    fig_roc.savefig(roc_path, dpi=300)
    plt.close(fig_roc)
    
    return pr_path, roc_path
