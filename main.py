"""
Main Pipeline Orchestrator for Credit Card Fraud Detection.
Academic Grade Implementation with End-to-End Benchmarking.
"""

import os
import sys
import argparse
import joblib
import pandas as pd

from data.download_data import ensure_dataset, RAW_CSV_PATH
from src.preprocess import load_data, split_and_scale
from src.eda import run_eda
from src.models import get_model, get_all_models
from src.evaluate import evaluate_model, plot_confusion_matrix, plot_benchmark_curves

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
MODELS_DIR = os.path.join(BASE_DIR, "models")
REPORTS_DIR = os.path.join(BASE_DIR, "reports")
FIGURES_DIR = os.path.join(REPORTS_DIR, "figures")

def main():
    parser = argparse.ArgumentParser(description="Credit Card Fraud Detection ML Pipeline")
    parser.add_argument("--eda", action="store_true", help="Run Exploratory Data Analysis & produce charts")
    parser.add_argument("--benchmark", action="store_true", help="Benchmark all classifiers and save comparison")
    parser.add_argument("--model", type=str, default="xgboost", help="Train specific model (xgboost, random_forest, lightgbm, smote_rf, logistic_regression)")
    parser.add_argument("--synthetic", action="store_true", help="Force synthetic data generator")
    parser.add_argument("--cost-fn", type=float, default=250.0, help="Financial penalty per False Negative (fraud allowed)")
    parser.add_argument("--cost-fp", type=float, default=15.0, help="Financial penalty per False Positive (customer friction)")
    args = parser.parse_args()

    os.makedirs(MODELS_DIR, exist_ok=True)
    os.makedirs(FIGURES_DIR, exist_ok=True)

    # 1. Dataset verification
    print("[1/4] Ensuring dataset availability...")
    data_path = ensure_dataset(allow_synthetic=True)
    df = load_data(data_path)

    # 2. EDA if requested
    if args.eda:
        print("[2/4] Executing EDA...")
        run_eda(df, output_dir=FIGURES_DIR)
        print("[+] EDA completed.")

    # 3. Preprocessing with Data Leakage Prevention
    print("\n[3/4] Preprocessing dataset (Stratified Split + RobustScaler)...")
    X_train, X_test, y_train, y_test, scalers = split_and_scale(df, test_size=0.2, random_state=42)
    joblib.dump(scalers, os.path.join(MODELS_DIR, "scalers.pkl"))
    
    n_neg = (y_train == 0).sum()
    n_pos = (y_train == 1).sum()
    class_ratio = n_neg / max(1, n_pos)
    print(f"      Train split: {len(X_train):,} samples ({n_pos} frauds, ratio ~{class_ratio:.1f}:1)")
    print(f"      Test split:  {len(X_test):,} samples ({(y_test==1).sum()} frauds)")

    # 4. Benchmarking or Single Model Training
    print("\n[4/4] Model Execution...")
    if args.benchmark:
        print("--- Running Multi-Model Benchmark Suite ---")
        models = get_all_models(class_ratio=class_ratio)
        results = []
        curves = {}

        for name, clf in models.items():
            print(f"--> Training: {name}...")
            clf.fit(X_train, y_train)
            metrics, cm, curve_data = evaluate_model(
                clf, X_test, y_test, model_name=name,
                cost_fn=args.cost_fn, cost_fp=args.cost_fp
            )
            plot_confusion_matrix(cm, name, output_dir=FIGURES_DIR)
            results.append(metrics)
            curves[name] = curve_data

        # Generate combined PR and ROC diagnostic plots
        pr_path, roc_path = plot_benchmark_curves(curves, output_dir=FIGURES_DIR)
        
        # Output summary comparison table
        results_df = pd.DataFrame(results).sort_values(by="PR-AUC", ascending=False)
        summary_csv = os.path.join(REPORTS_DIR, "benchmark_results.csv")
        results_df.to_csv(summary_csv, index=False)
        
        print("\n" + "="*85)
        print("                      BENCHMARK RESULTS SUMMARY")
        print("="*85)
        print(results_df.to_markdown(index=False))
        print("="*85)
        print(f"[+] Diagnostic Curves: {pr_path} | {roc_path}")
        print(f"[+] Full Metrics CSV:  {summary_csv}")

    else:
        print(f"--- Training Single Model: {args.model.upper()} ---")
        clf = get_model(args.model, class_ratio=class_ratio)
        clf.fit(X_train, y_train)
        
        metrics, cm, _ = evaluate_model(
            clf, X_test, y_test, model_name=args.model.upper(),
            cost_fn=args.cost_fn, cost_fp=args.cost_fp
        )
        cm_path = plot_confusion_matrix(cm, args.model.upper(), output_dir=FIGURES_DIR)
        
        model_save_path = os.path.join(MODELS_DIR, f"{args.model}_model.pkl")
        joblib.dump(clf, model_save_path)
        
        print("\n" + "="*60)
        print(f"      EVALUATION RESULTS: {args.model.upper()}")
        print("="*60)
        for k, v in metrics.items():
            print(f"{k:20s}: {v}")
        print("="*60)
        print(f"[+] Model checkpoint: {model_save_path}")
        print(f"[+] Confusion Matrix: {cm_path}")

if __name__ == "__main__":
    main()
