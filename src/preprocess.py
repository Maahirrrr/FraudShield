"""
Data preprocessing, feature engineering, and stratified splitting pipeline.
Enforces rigorous data leakage prevention by fitting scalers exclusively on train splits.
"""

import os
import joblib
import pandas as pd
import numpy as np
from sklearn.model_selection import train_test_split, StratifiedKFold
from sklearn.preprocessing import RobustScaler, StandardScaler

def load_data(filepath):
    """Load transaction dataset from CSV."""
    if not os.path.exists(filepath):
        raise FileNotFoundError(f"Dataset not found at {filepath}")
    df = pd.read_csv(filepath)
    return df

def get_feature_and_target(df, target_col="Class"):
    """Separate features and target label."""
    X = df.drop(columns=[target_col])
    y = df[target_col]
    return X, y

def split_and_scale(df, test_size=0.2, random_state=42, scaler_type="robust"):
    """
    Perform stratified split followed by leakage-free feature scaling.
    - Fits scaler strictly on training set.
    - Applies transformation to both training and test sets.
    """
    X, y = get_feature_and_target(df)
    
    # Stratified split to preserve class ratio
    X_train, X_test, y_train, y_test = train_test_split(
        X, y, test_size=test_size, stratify=y, random_state=random_state
    )
    
    # Scale 'Amount' and 'Time' using RobustScaler (outlier-resistant)
    scaler_time = RobustScaler() if scaler_type == "robust" else StandardScaler()
    scaler_amount = RobustScaler() if scaler_type == "robust" else StandardScaler()
    
    # Work on copies to prevent SettingWithCopy warnings
    X_train = X_train.copy()
    X_test = X_test.copy()
    
    if "Time" in X_train.columns:
        X_train["Scaled_Time"] = scaler_time.fit_transform(X_train[["Time"]])
        X_test["Scaled_Time"] = scaler_time.transform(X_test[["Time"]])
        X_train.drop(columns=["Time"], inplace=True)
        X_test.drop(columns=["Time"], inplace=True)
        
    if "Amount" in X_train.columns:
        X_train["Scaled_Amount"] = scaler_amount.fit_transform(X_train[["Amount"]])
        X_test["Scaled_Amount"] = scaler_amount.transform(X_test[["Amount"]])
        X_train.drop(columns=["Amount"], inplace=True)
        X_test.drop(columns=["Amount"], inplace=True)
        
    scalers = {
        "scaler_time": scaler_time if "Time" in X.columns else None,
        "scaler_amount": scaler_amount if "Amount" in X.columns else None
    }
    
    return X_train, X_test, y_train, y_test, scalers

def get_stratified_cv(n_splits=5, random_state=42):
    """Return StratifiedKFold cross-validator for imbalanced data."""
    return StratifiedKFold(n_splits=n_splits, shuffle=True, random_state=random_state)
