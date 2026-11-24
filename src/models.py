"""
Model factory for Credit Card Fraud Detection.
Includes baseline linear classifiers, cost-sensitive gradient boosting,
leakage-free resampling pipelines, and unsupervised anomaly detection.
"""

from sklearn.linear_model import LogisticRegression
from sklearn.ensemble import RandomForestClassifier, IsolationForest
from xgboost import XGBClassifier
from lightgbm import LGBMClassifier
from imblearn.pipeline import Pipeline as ImbPipeline
from imblearn.over_sampling import SMOTE
from imblearn.under_sampling import RandomUnderSampler

def get_model(model_name="xgboost", class_ratio=580, random_state=42):
    """
    Return initialized model instance configured for heavy class imbalance.
    
    Parameters:
    - model_name: 'logistic_regression', 'random_forest', 'xgboost', 'lightgbm', 'smote_rf', 'isolation_forest'
    - class_ratio: Ratio of negative to positive samples (for scale_pos_weight)
    """
    model_name = model_name.lower()
    
    if model_name in ["logistic_regression", "lr"]:
        return LogisticRegression(
            class_weight="balanced",
            max_iter=1000,
            solver="lbfgs",
            random_state=random_state
        )
        
    elif model_name in ["random_forest", "rf"]:
        return RandomForestClassifier(
            n_estimators=100,
            class_weight="balanced",
            max_depth=12,
            n_jobs=-1,
            random_state=random_state
        )
        
    elif model_name in ["xgboost", "xgb"]:
        return XGBClassifier(
            n_estimators=150,
            max_depth=5,
            learning_rate=0.08,
            scale_pos_weight=class_ratio,
            eval_metric="aucpr",
            random_state=random_state,
            n_jobs=-1
        )
        
    elif model_name in ["lightgbm", "lgb"]:
        return LGBMClassifier(
            n_estimators=150,
            max_depth=5,
            learning_rate=0.08,
            class_weight="balanced",
            random_state=random_state,
            n_jobs=-1,
            verbose=-1
        )
        
    elif model_name in ["smote_rf"]:
        # Imblearn pipeline: SMOTE up to 10% fraud ratio, then under-sample majority
        return ImbPipeline([
            ("smote", SMOTE(sampling_strategy=0.1, random_state=random_state)),
            ("undersample", RandomUnderSampler(sampling_strategy=0.5, random_state=random_state)),
            ("classifier", RandomForestClassifier(n_estimators=100, max_depth=10, n_jobs=-1, random_state=random_state))
        ])
        
    elif model_name in ["isolation_forest", "iforest"]:
        return IsolationForest(
            n_estimators=150,
            contamination=1.0 / max(1, class_ratio),
            random_state=random_state,
            n_jobs=-1
        )
        
    else:
        raise ValueError(f"Unknown model name: {model_name}. Choose from: 'logistic_regression', 'random_forest', 'xgboost', 'lightgbm', 'smote_rf', 'isolation_forest'")

def get_all_models(class_ratio=580, random_state=42):
    """Return dictionary of all available supervised models for benchmarking."""
    return {
        "Logistic Regression (Balanced)": get_model("logistic_regression", class_ratio, random_state),
        "Random Forest (Balanced)": get_model("random_forest", class_ratio, random_state),
        "XGBoost (Cost-Sensitive)": get_model("xgboost", class_ratio, random_state),
        "LightGBM (Balanced)": get_model("lightgbm", class_ratio, random_state),
        "SMOTE + Random Forest": get_model("smote_rf", class_ratio, random_state)
    }
