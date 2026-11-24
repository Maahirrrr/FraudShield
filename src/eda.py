"""
Exploratory Data Analysis (EDA) module for Credit Card Fraud Detection.
Generates statistical summaries and publication-ready diagnostic plots.
"""

import os
import matplotlib
matplotlib.use("Agg")  # Non-interactive backend
import matplotlib.pyplot as plt
import seaborn as sns
import pandas as pd
import numpy as np

def run_eda(df, output_dir="reports/figures"):
    """
    Execute exploratory analysis on the dataset:
    1. Class distribution & Imbalance ratio
    2. Amount distribution by Class
    3. Correlation ranking against target Class
    4. Save figures to disk
    """
    os.makedirs(output_dir, exist_ok=True)
    
    print("\n" + "="*50)
    print("      EXPLORATORY DATA ANALYSIS (EDA)")
    print("="*50)
    
    total_samples = len(df)
    class_counts = df["Class"].value_counts()
    n_legit = class_counts.get(0, 0)
    n_fraud = class_counts.get(1, 0)
    fraud_pct = (n_fraud / total_samples) * 100
    
    print(f"Total Transactions: {total_samples:,}")
    print(f"Legitimate (Class 0): {n_legit:,} ({100 - fraud_pct:.3f}%)")
    print(f"Fraudulent (Class 1): {n_fraud:,} ({fraud_pct:.3f}%)")
    print(f"Imbalance Ratio: 1 fraud per {int(n_legit / max(1, n_fraud))} legitimate transactions")
    
    # Amount summary by class
    print("\n--- Transaction Amount Statistics ---")
    amount_summary = df.groupby("Class")["Amount"].describe()
    print(amount_summary[["count", "mean", "std", "min", "50%", "max"]])
    
    # 1. Plot Class Distribution
    fig, ax = plt.subplots(figsize=(6, 4))
    sns.countplot(data=df, x="Class", hue="Class", palette=["#2ecc71", "#e74c3c"], legend=False, ax=ax)
    ax.set_title("Class Distribution (0: Normal, 1: Fraud)")
    ax.set_yscale("log")
    ax.set_ylabel("Count (Log Scale)")
    plt.tight_layout()
    dist_plot_path = os.path.join(output_dir, "class_distribution.png")
    fig.savefig(dist_plot_path, dpi=300)
    plt.close(fig)
    print(f"[+] Saved: {dist_plot_path}")
    
    # 2. Plot Amount Distribution by Class
    fig, ax = plt.subplots(figsize=(8, 4))
    sns.boxplot(data=df, x="Class", y="Amount", hue="Class", palette=["#2ecc71", "#e74c3c"], legend=False, ax=ax, showfliers=False)
    ax.set_title("Transaction Amount Distribution (Excluding Outliers for Visualization)")
    ax.set_xticks([0, 1])
    ax.set_xticklabels(["Legitimate (0)", "Fraudulent (1)"])
    plt.tight_layout()
    box_plot_path = os.path.join(output_dir, "amount_boxplot.png")
    fig.savefig(box_plot_path, dpi=300)
    plt.close(fig)
    print(f"[+] Saved: {box_plot_path}")
    
    # 3. Top Correlated Features with Fraud
    corr = df.corr()["Class"].drop("Class").sort_values()
    top_pos = corr.tail(5)
    top_neg = corr.head(5)
    top_corr = pd.concat([top_neg, top_pos])
    
    fig, ax = plt.subplots(figsize=(8, 5))
    top_corr.plot(kind="barh", color=(top_corr > 0).map({True: "#e74c3c", False: "#3498db"}), ax=ax)
    ax.set_title("Top 10 Most Correlated Features with Fraud (Class)")
    ax.set_xlabel("Pearson Correlation Coefficient")
    ax.axvline(0, color="grey", linestyle="--", alpha=0.7)
    plt.tight_layout()
    corr_plot_path = os.path.join(output_dir, "correlation_bar.png")
    fig.savefig(corr_plot_path, dpi=300)
    plt.close(fig)
    print(f"[+] Saved: {corr_plot_path}")
    
    print("="*50 + "\n")
    return {
        "total_samples": total_samples,
        "n_legit": n_legit,
        "n_fraud": n_fraud,
        "fraud_pct": fraud_pct,
        "top_corr": top_corr.to_dict()
    }
