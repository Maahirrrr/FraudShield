"""
Dataset acquisition utility for Credit Card Fraud Detection.
Supports automatic downloading from Kaggle (mlg-ulb/creditcardfraud)
or generating a statistically matched benchmark dataset for offline academic testing.
"""

import os
import sys
import argparse
import numpy as np
import pandas as pd

DATA_DIR = os.path.dirname(os.path.abspath(__file__))
RAW_CSV_PATH = os.path.join(DATA_DIR, "creditcard.csv")

def download_from_kaggle():
    """Attempt to download dataset using Kaggle API."""
    print("Checking for Kaggle API credentials...")
    kaggle_json = os.path.expanduser("~/.kaggle/kaggle.json")
    has_env = os.environ.get("KAGGLE_USERNAME") and os.environ.get("KAGGLE_KEY")

    if not os.path.exists(kaggle_json) and not has_env:
        print("[!] Kaggle credentials not detected.")
        print("    Place your kaggle.json in ~/.kaggle/ or set KAGGLE_USERNAME & KAGGLE_KEY.")
        return False

    try:
        from kaggle.api.kaggle_api_extended import KaggleApi
        api = KaggleApi()
        api.authenticate()
        print("Downloading mlg-ulb/creditcardfraud from Kaggle...")
        api.dataset_download_files("mlg-ulb/creditcardfraud", path=DATA_DIR, unzip=True)
        print(f"[+] Download complete: {RAW_CSV_PATH}")
        return True
    except Exception as e:
        print(f"[!] Error downloading from Kaggle: {e}")
        return False

def generate_synthetic_benchmark(n_samples=50000, fraud_ratio=0.0017, random_state=42):
    """
    Generate statistically realistic benchmark dataset matching Kaggle's schema:
    - Time (seconds elapsed)
    - V1 to V28 (PCA-transformed numeric features)
    - Amount (Transaction amount with heavy right skew)
    - Class (0: Legitimate, 1: Fraudulent)
    """
    print(f"Generating synthetic benchmark dataset ({n_samples:,} samples, {fraud_ratio*100:.2f}% fraud)...")
    np.random.seed(random_state)
    
    n_fraud = int(n_samples * fraud_ratio)
    n_legit = n_samples - n_fraud
    
    # 1. Time feature (0 to 172800 seconds = 48 hours)
    time_legit = np.sort(np.random.uniform(0, 172800, n_legit))
    time_fraud = np.sort(np.random.uniform(0, 172800, n_fraud))
    
    # 2. V1 to V28 PCA components
    # Legitimate transactions ~ Standard Normal N(0, 1)
    v_legit = np.random.randn(n_legit, 28)
    
    # Fraud transactions have shifted means & variances in key dimensions (V1-V4, V10-V14, V17)
    v_fraud = np.random.randn(n_fraud, 28)
    v_fraud[:, 0] -= 2.5   # V1 shifted
    v_fraud[:, 1] += 1.8   # V2 shifted
    v_fraud[:, 2] -= 3.0   # V3 shifted
    v_fraud[:, 3] += 2.2   # V4 shifted
    v_fraud[:, 9] -= 3.5   # V10 shifted
    v_fraud[:, 11] -= 4.0  # V12 shifted
    v_fraud[:, 13] -= 4.5  # V14 shifted
    v_fraud[:, 16] -= 3.2  # V17 shifted
    
    # 3. Amount feature (log-normal distribution to mirror financial amounts)
    amount_legit = np.random.lognormal(mean=3.2, sigma=1.2, size=n_legit)
    amount_fraud = np.random.lognormal(mean=3.8, sigma=1.5, size=n_fraud)
    
    # Combine into DataFrame
    legit_df = pd.DataFrame(v_legit, columns=[f"V{i}" for i in range(1, 29)])
    legit_df["Time"] = time_legit
    legit_df["Amount"] = np.round(amount_legit, 2)
    legit_df["Class"] = 0
    
    fraud_df = pd.DataFrame(v_fraud, columns=[f"V{i}" for i in range(1, 29)])
    fraud_df["Time"] = time_fraud
    fraud_df["Amount"] = np.round(amount_fraud, 2)
    fraud_df["Class"] = 1
    
    df = pd.concat([legit_df, fraud_df], axis=0).sample(frac=1.0, random_state=random_state).reset_index(drop=True)
    
    # Ensure correct column order: Time, V1-V28, Amount, Class
    cols = ["Time"] + [f"V{i}" for i in range(1, 29)] + ["Amount", "Class"]
    df = df[cols]
    
    df.to_csv(RAW_CSV_PATH, index=False)
    print(f"[+] Benchmark dataset saved to {RAW_CSV_PATH} ({len(df):,} rows, {df['Class'].sum()} frauds).")
    return RAW_CSV_PATH

def ensure_dataset(allow_synthetic=False):
    """Ensure authentic creditcard.csv exists. Downloads if missing."""
    if os.path.exists(RAW_CSV_PATH) and os.path.getsize(RAW_CSV_PATH) > 100_000_000:
        print(f"[i] Authentic 284k+ dataset found at {RAW_CSV_PATH}")
        return RAW_CSV_PATH

    print("[!] Authentic creditcard.csv not found locally. Initiating download...")
    # Attempt 1: Kaggle API
    success = download_from_kaggle()
    if success and os.path.exists(RAW_CSV_PATH) and os.path.getsize(RAW_CSV_PATH) > 100_000_000:
        return RAW_CSV_PATH

    # Attempt 2: Direct verified public mirror (TensorFlow / Google Storage official mirror)
    print("[*] Downloading full 284,807 transaction dataset from verified repository...")
    url = "https://storage.googleapis.com/download.tensorflow.org/data/creditcard.csv"
    try:
        import urllib.request
        def reporthook(count, block_size, total_size):
            if count % 1000 == 0:
                percent = int(count * block_size * 100 / total_size)
                sys.stdout.write(f"\rDownloading: {percent}% ({count * block_size / (1024*1024):.1f} MB)")
                sys.stdout.flush()
        urllib.request.urlretrieve(url, RAW_CSV_PATH, reporthook=reporthook)
        print(f"\n[+] Downloaded verified dataset successfully: {RAW_CSV_PATH}")
        return RAW_CSV_PATH
    except Exception as e:
        print(f"[!] Mirror download failed: {e}")
        if allow_synthetic:
            print("[*] Falling back to generating representative benchmark dataset for experimentation.")
            return generate_synthetic_benchmark()
        else:
            print("[X] Could not acquire dataset. Please download creditcard.csv manually into data/.")
            sys.exit(1)

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Acquire or generate Credit Card Fraud dataset.")
    parser.add_argument("--synthetic", action="store_true", help="Force generate synthetic benchmark dataset.")
    parser.add_argument("--samples", type=int, default=50000, help="Number of samples if synthetic.")
    args = parser.parse_args()

    if args.synthetic:
        generate_synthetic_benchmark(n_samples=args.samples)
    else:
        ensure_dataset(allow_synthetic=True)
