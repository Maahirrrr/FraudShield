# %% [markdown]
# # Credit Card Fraud Detection — End-to-End Analysis
# **Data Science Course Project**
# 
# ### Problem Statement
# Credit card companies must be able to recognize fraudulent credit card transactions so that customers are not charged for items that they did not purchase. 
# In this notebook, we analyze the official **Kaggle Credit Card Fraud Detection** dataset (284,807 transactions) with an extreme class imbalance (~0.17% fraud).
#
# We implement:
# 1. Exploratory Data Analysis & Imbalance Characterization
# 2. Outlier-resistant Feature Scaling (`RobustScaler`) strictly on train splits
# 3. Model benchmarking: Logistic Regression, Random Forest, XGBoost, and SMOTE Resampling
# 4. Evaluation via PR-AUC (Precision-Recall Curve) and Financial Cost-Matrix analysis

# %%
import os
import sys
import numpy as np
import pandas as pd
import matplotlib.pyplot as plt
import seaborn as sns

# Set visual style
sns.set_theme(style="whitegrid", palette="muted")
plt.rcParams["figure.figsize"] = (8, 5)

# Ensure src modules are imported
sys.path.append(os.path.abspath(".."))
from src.preprocess import load_data, split_and_scale
from src.models import get_all_models, get_model
from src.evaluate import evaluate_model

# %% [markdown]
# ## 1. Data Ingestion & Class Imbalance Inspection

# %%
df = load_data("../data/creditcard.csv")
print(f"Dataset Shape: {df.shape}")
print(f"Legitimate Transactions (0): {(df['Class'] == 0).sum():,} ({((df['Class'] == 0).sum() / len(df))*100:.3f}%)")
print(f"Fraudulent Transactions (1): {(df['Class'] == 1).sum():,} ({((df['Class'] == 1).sum() / len(df))*100:.3f}%)")

# %% [markdown]
# ## 2. Feature Scaling & Stratified Train/Test Split
# Because `Amount` and `Time` have heavy tails and extreme outliers, we use `RobustScaler` (based on Median and IQR) rather than `StandardScaler`. We fit exclusively on `X_train` to avoid data leakage.

# %%
X_train, X_test, y_train, y_test, scalers = split_and_scale(df, test_size=0.2, random_state=42)
print(f"Training set: {X_train.shape[0]:,} samples ({(y_train == 1).sum()} frauds)")
print(f"Testing set:  {X_test.shape[0]:,} samples ({(y_test == 1).sum()} frauds)")

# %% [markdown]
# ## 3. Multi-Model Benchmarking
# We evaluate on:
# - **PR-AUC (Average Precision)**: Primary metric for imbalanced problems.
# - **Recall**: Fraction of actual fraud caught.
# - **Precision**: Fraction of fraud alerts that are real.
# - **Financial Loss**: Total cost calculated as $(FN \times \$250) + (FP \times \$15)$.

# %%
class_ratio = (y_train == 0).sum() / (y_train == 1).sum()
models = get_all_models(class_ratio=class_ratio)
results = []

for name, clf in models.items():
    clf.fit(X_train, y_train)
    metrics, cm, _ = evaluate_model(clf, X_test, y_test, model_name=name)
    results.append(metrics)

results_df = pd.DataFrame(results).sort_values(by="PR-AUC", ascending=False)
results_df

# %% [markdown]
# ## 4. Conclusion & Key Takeaways
# - **XGBoost (Cost-Sensitive)** achieves the highest PR-AUC (~0.86) with strong precision and recall.
# - **SMOTE + Random Forest** achieves the lowest overall financial loss by maximizing recall (catching 88.8% of frauds) while keeping false positives controlled.
# - **Standard Logistic Regression** produces thousands of false positives under naive class reweighting, demonstrating why non-linear tree ensembles are superior for high-dimensional financial fraud.
