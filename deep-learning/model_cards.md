# FraudShield — Deep Learning Model Summaries & Ethics Analysis
**Course**: Integrated B.Tech 3rd Year Project (Deep Learning Specialization)  
**Deliverable**: Empirically Reproducible Model Cards & AI Ethics Whitepaper

---

## 1. Model Card 1: Bottleneck Autoencoder (`deep-learning/autoencoder.py`)

### 1.1 Model Metadata & Purpose
* **Architecture**: `FraudAutoencoder` (PyTorch 2.15, CPU inference)
* **Purpose**: Unsupervised zero-day anomaly detector trained exclusively on legitimate transactions (`Class = 0`). Learns the manifold of normal purchasing behaviour and flags deviations via Mean Squared Reconstruction Error (MSE).

### 1.2 Architecture & Training Configuration
```text
Input (30 features: V1-V28, Scaled_Time, Scaled_Amount)
  ├── Linear(30 -> 20) + BatchNorm1d + LeakyReLU(0.1) + Dropout(0.1)
  ├── Linear(20 -> 12) + BatchNorm1d + LeakyReLU(0.1)
  ├── Linear(12 -> 6)   <-- 5x Compression Bottleneck
  ├── Linear(6 -> 12)  + BatchNorm1d + LeakyReLU(0.1) + Dropout(0.1)
  ├── Linear(12 -> 20) + BatchNorm1d + LeakyReLU(0.1)
  └── Linear(20 -> 30) (Reconstruction Output)
```
* **Loss Function**: MSE $\mathcal{L} = \frac{1}{D}\sum_{i=1}^{D} (x_i - \hat{x}_i)^2$
* **Optimizer**: AdamW (`lr=1e-3`, `weight_decay=1e-5`, 20 epochs, batch size 256)
* **Training Data**: **217,451 confirmed legitimate transactions** (`Class = 0`), zero fraud samples seen during training.

### 1.3 Empirical Full Test Results (`N = 56,962`, stratified holdout, 98 frauds)
* **PR-AUC (Continuous MSE Ranking)**: **0.4598**
* **ROC-AUC**: **0.9536**
* **Calibrated Validation Operating Threshold (`p99.5` on holdout legit)**: `MSE >= 2.8441`
  * **TP**: 78 | **FP**: 308 | **TN**: 56,556 | **FN**: 20
  * **Recall**: **79.59%** | **Precision**: **20.21%** | **F1-Score**: **0.3223**
  * **Financial Cost (`FN×$250 + FP×$15`)**: **\$9,620.00**
* **Per-Feature Explainability**: In live inference (`model-service/app.py`), FraudShield computes squared reconstruction residual $\Delta_i = (x_i - \hat{x}_i)^2$ across all 30 features and surfaces the top 3 contributing vectors directly in transaction reasons.

---

## 2. Model Card 2: Chronological LSTM Sequence Classifier (`deep-learning/lstm_sequence.py`)

### 2.1 Model Metadata & Purpose
* **Architecture**: `FraudLSTM` (PyTorch 2.15)
* **Purpose**: Sequential temporal fraud detector trained across strictly **time-ordered (`Time`-sorted) 5-transaction sliding windows** (`seq_len = 5`). Detects card-testing velocity bursts that isolated single-row tabular models miss.

### 2.2 Architecture & Calibration
* **Input Sequence**: `(batch, seq_len=5, features=30)` chronological sliding windows.
* **Recurrent Backbone**: Single-layer LSTM (`hidden_dim=32`), final hidden state routed to `Dropout(0.2) -> Linear(32 -> 16) -> ReLU -> Linear(16 -> 1)`.
* **Loss Function**: Calibrated `BCEWithLogitsLoss(pos_weight=22.82)` ($\min(30, \sqrt{\text{neg/pos}})$) over 205,056 training sequences, avoiding both gradient washout (weight=1) and probability saturation.

### 2.3 Empirical Chronological Holdout Results (`N = 56,958`, 75 future unseen frauds)
* **PR-AUC**: **0.8088** (surpasses isolated tabular XGBoost on the exact same future-time chronological holdout!)
* **ROC-AUC**: **0.9856**
* **Validation-Tuned Decision Threshold**: `0.60`
  * **TP**: 59 | **FP**: 29 | **TN**: 56,854 | **FN**: 16
  * **Recall**: **78.67%** | **Precision**: **67.05%** | **F1-Score**: **0.7239**
  * **Financial Cost (`FN×$250 + FP×$15`)**: **\$4,435.00**

---

## 3. Verified Multi-Model Comparison (`benchmark_results.csv` & `dl_benchmark_results.csv`)

### 3.1 Stratified Holdout Evaluation (`N = 56,962`, 98 frauds)
| Model Architecture | PR-AUC (Primary) | ROC-AUC | Recall | Precision | F1-Score | TP | FP | FN | Financial Cost (\$)* |
| :--- | :---: | :---: | :---: | :---: | :---: | :-: | :-: | :-: | :---: |
| **XGBoost (Cost-Sensitive)** | **0.8596** | 0.9729 | 85.71% | 61.31% | 0.7149 | 84 | 53 | 14 | \$4,295 |
| **LightGBM (Balanced)** | 0.8474 | 0.9770 | 84.69% | 63.85% | 0.7281 | 83 | 47 | 15 | \$4,455 |
| **SMOTE + Random Forest** | 0.8410 | **0.9793** | 88.78% | 54.38% | 0.6744 | 87 | 73 | 11 | **\$3,845** |
| **Random Forest (Balanced)** | 0.8389 | 0.9765 | 82.65% | **71.05%** | **0.7642** | 81 | 33 | 17 | \$4,745 |
| **Logistic Regression (Baseline)** | 0.7175 | 0.9720 | **91.84%** | 6.09% | 0.1142 | 90 | 1,388 | 8 | \$22,820 |
| **Bottleneck Autoencoder (`@p99.5`)** | 0.4598 | 0.9536 | 79.59% | 20.21% | 0.3223 | 78 | 308 | 20 | \$9,620 |

### 3.2 Strict Chronological Holdout Evaluation (`Time` 80/20 split, unseen future hours)
| Model Architecture | PR-AUC | ROC-AUC | Recall | Precision | F1-Score | TP | FP | FN | Financial Cost (\$)* |
| :--- | :---: | :---: | :---: | :---: | :---: | :-: | :-: | :-: | :---: |
| **LSTM Sequence Model (`seq_len=5`)** | **0.8088** | **0.9856** | **78.67%** | 67.05% | 0.7239 | 59 | 29 | 16 | **\$4,435** |
| **XGBoost (Tabular Single-Row)** | 0.7933 | 0.9838 | 76.00% | **76.00%** | **0.7600** | 57 | 18 | 18 | \$4,770 |

*\*Cost matrix: \$250 per missed fraud (FN) + \$15 per false alarm dispute (FP).*

### 3.3 XGBoost Threshold Sensitivity (`notebooks/threshold_analysis.py` Exact Output, Stratified Test Set)
| Decision Threshold | TP | FP | FN | Precision | Recall | F1-Score | Financial Loss (\$) |
| :---: | :-: | :-: | :-: | :---: | :---: | :---: | :---: |
| `0.30` | 86 | 104 | 12 | 45.26% | 87.76% | 0.5972 | \$4,560.00 |
| `0.40` | 84 | 70 | 14 | 54.55% | 85.71% | 0.6667 | \$4,550.00 |
| `0.50` | 84 | 53 | 14 | 61.31% | 85.71% | 0.7149 | \$4,295.00 |
| `0.60` | 84 | 38 | 14 | 68.85% | 85.71% | 0.7636 | **\$4,070.00** |
| **`0.70` (Production BLOCK)** | 82 | 28 | 16 | 74.55% | 83.67% | 0.7885 | \$4,420.00 |
| `0.80` | 81 | 22 | 17 | 78.64% | 82.65% | 0.8060 | \$4,580.00 |

> **Why Hybrid Ensemble in Live Serving (`model-service/app.py`)?** XGBoost achieves the highest tabular PR-AUC (`0.8596`), zero-overhead feature importance explainability, and fast inference; the Autoencoder catches unsupervised out-of-distribution anomalies with feature-level MSE residuals; and on strict future chronological sequences, `FraudLSTM` actually beats isolated XGBoost (`0.8088` vs `0.7933` PR-AUC, reducing chronological loss by \$335). Additionally, for live D2C INR orders, `app.py` applies FX parity (`Amount / 85.0`) before applying the ULB `RobustScaler` so median ₹1,899 (~€22) orders stay centered in the trained feature manifold.

---

## 4. AI Ethics, Fairness & DPDP Act 2023 Safeguards

1. **Feature Minimisation**: Names, raw card PANs/CVVs, phone numbers, and street addresses are never ingested into the scoring tensors; all features operate strictly on anonymized PCA vectors + scaled amount/time + device hashes.
2. **Tri-State Friction Reduction**: Rather than hard-blocking every borderline alert (`0.35 <= risk < 0.70`), **VERIFY** triggers a lightweight OTP step-up (`POST /api/orders/verify`) so legitimate customers are never turned away unfairly.
3. **Human-in-the-Loop Override**: Merchants retain final override authority (`POST /api/override`), recording ground-truth feedback to prevent selection bias / feedback-loop drift.
4. **Right to Erasure (`DELETE /api/data/erase`)**: Executes deterministic deletion across both `transactions.jsonl` and `analysis_export.csv` under thread lock.
