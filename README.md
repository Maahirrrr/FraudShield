# Credit Card Fraud Detection — Academic Data Science Project

A modular machine learning project designed to benchmark techniques for extreme class imbalance in financial transaction fraud detection.

---

## 📌 1. Project Overview

Detecting fraudulent transactions is a classic **extreme class imbalance** problem. In real-world payment networks (and in the benchmark Kaggle ULB dataset), legitimate transactions account for **>99.8%** of total volume, while fraud represents **<0.2%**.

### Key Challenges Addressed:
1. **Severe Class Imbalance**: Standard classification models optimize for global accuracy, predicting all transactions as legitimate (99.8% accuracy) while missing 100% of fraud.
2. **Asymmetric Misclassification Costs**: Allowing a fraudulent transaction through (False Negative) results in direct financial chargebacks ($200+), whereas flagging a legitimate transaction (False Positive) incurs only small operational friction ($15).
3. **Data Leakage Prevention**: Preprocessing (scaling and resampling) is strictly fitted on training partitions only.

---

## 🗂️ 2. Repository Structure

```text
credit_card_fraud_detection/
├── data/
│   ├── download_data.py          # Kaggle API downloader + fallback benchmark generator
│   └── creditcard.csv            # Dataset (downloaded or generated)
├── src/
│   ├── preprocess.py             # Stratified splitting and RobustScaler pipeline
│   ├── eda.py                    # Imbalance profiling, correlation, and boxplots
│   ├── models.py                 # Logistic Regression, RF, XGBoost, LightGBM, SMOTE-RF
│   └── evaluate.py               # PR-AUC, ROC-AUC, Cost Matrix & diagnostic plots
├── reports/
│   ├── figures/                  # Generated diagnostic curves and heatmaps
│   └── benchmark_results.csv     # Model comparison table
├── models/                       # Serialized models and scalers (.pkl)
├── requirements.txt              # Pinned dependencies
├── main.py                       # CLI execution entry point
└── README.md                     # Documentation
```

---

## 🚀 3. Quickstart & Usage

### 3.1 Setup Environment

```bash
# Navigate to project directory
cd /home/maahir/.gemini/antigravity/scratch/credit_card_fraud_detection

# Activate virtual environment
source .venv/bin/activate
```

### 3.2 Dataset Acquisition (Kaggle or Synthetic Benchmark)

- **Option A: Official Kaggle Dataset**
  Place your `kaggle.json` file inside `~/.kaggle/` and run:
  ```bash
  python data/download_data.py
  ```
- **Option B: Automatic Benchmark**
  If Kaggle credentials are not provided, the script automatically generates an exact-schema synthetic dataset (50,000 samples, 0.17% fraud rate) with realistic distributions for testing.

### 3.3 Run Exploratory Data Analysis (EDA)

```bash
python main.py --eda
```
Generates diagnostic visual plots in `reports/figures/`:
- `class_distribution.png`: Log-scale class imbalance visual.
- `amount_boxplot.png`: Transaction amount spread between fraud vs legit.
- `correlation_bar.png`: Top 10 correlated features with fraud.

### 3.4 Benchmark All Models

```bash
python main.py --benchmark
```
Trains and evaluates:
1. **Logistic Regression (Class Weighted)**
2. **Random Forest (Balanced)**
3. **XGBoost (Cost-Sensitive `scale_pos_weight`)**
4. **LightGBM (Balanced)**
5. **SMOTE + Random Forest (imblearn pipeline)**

Outputs:
- Metric comparison: **PR-AUC, ROC-AUC, Recall, Precision, F1-Score**
- Business Cost: **Total Financial Loss ($)** based on cost matrix
- Plots: `benchmark_pr_curves.png` and `benchmark_roc_curves.png`

### 3.5 Train and Save a Single Model

```bash
python main.py --model xgboost
```
Saves the trained model to `models/xgboost_model.pkl` and scalers to `models/scalers.pkl`.

---

## 📊 4. Evaluation Methodology

| Metric | Why It Matters in Fraud Detection |
| :--- | :--- |
| **PR-AUC (Primary)** | Evaluates precision vs recall exclusively across the minority class without inflation by massive True Negatives. |
| **Recall (Sensitivity)** | $\frac{\text{TP}}{\text{TP} + \text{FN}}$: Measures the percentage of total fraud caught. |
| **Precision** | $\frac{\text{TP}}{\text{TP} + \text{FP}}$: Measures how many fraud alerts were genuine. |
| **Financial Cost** | $\text{Loss} = (\text{FN} \times \$250) + (\text{FP} \times \$15)$ |

---

## 💡 5. Recommended IDE Workspace

To open this project directly in your workspace:
Set active workspace path to:
`/home/maahir/.gemini/antigravity/scratch/credit_card_fraud_detection`
