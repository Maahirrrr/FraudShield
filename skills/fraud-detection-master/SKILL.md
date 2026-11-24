---
name: fraud-detection-master
description: Production-grade fraud detection engineering, anomaly detection, threshold calibration, and imbalanced dataset modeling playbooks.
metadata:
  category: ai-ml-fintech
  domain: fraud-risk-intelligence
---

# Fraud Detection Master Playbook

## 1. Metric Hierarchy for Imbalanced Datasets (0.17% Base Rate)
- **Primary Metric**: PR-AUC (Precision-Recall Area Under Curve). Standard ROC-AUC is misleading on 99.8% legitimate datasets.
- **Cost Matrix Equation**:
  $$\text{Total Cost} = (\text{False Negatives} \times \$250) + (\text{False Positives} \times \$15)$$
- **Calibrated Three-Tier Decision Policy**:
  - `Risk Score < 0.35`: **APPROVE** (frictionless path)
  - `0.35 <= Risk Score < 0.70`: **VERIFY** (step-up 2FA/OTP authentication)
  - `Risk Score >= 0.70`: **BLOCK** (hard block with explainable deflection reasons)

## 2. Multi-Engine Architecture
1. **Classical Gradient Boosting (XGBoost / LightGBM)**: Fast (<15ms) tabular feature scoring with cost-sensitive weighting (`scale_pos_weight = 577`).
2. **Sequential Velocity (LSTM)**: Sequential recurrent network (`seq_len=5`) tracking rapid card-testing bursts and velocity spikes.
3. **Unsupervised Outlier Detection (Bottleneck Autoencoder)**: 30->6 latent->30 reconstruction MSE identifying zero-day attack vectors.
4. **Resilient Local Fallback**: Deterministic heuristic rules executed in Java when Python inference times out (>300ms).
