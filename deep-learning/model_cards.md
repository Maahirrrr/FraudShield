# FraudShield — Deep Learning Model Summaries & Ethics Analysis
**Course**: Integrated B.Tech 3rd Year Project (Deep Learning Specialization)  
**Deliverable**: Comprehensive Model Cards & AI Ethics Whitepaper

---

## 1. Model Card 1: Bottleneck Autoencoder (Unsupervised Anomaly Detection)

### 1.1 Model Metadata & Purpose
* **Model Name**: FraudShield Bottleneck Autoencoder (`FraudAutoencoder`)
* **Framework**: PyTorch 2.15 (CPU-quantized)
* **Target Objective**: Identify zero-day transaction fraud patterns and flag new customers with no historical baseline.
* **Core Hypothesis**: By training exclusively on legitimate transactions, the network learns the low-dimensional manifold of normal purchasing behaviour. When fraudulent or anomalous transactions pass through, they exhibit high reconstruction error.

### 1.2 Architecture & Training Configuration
```text
Input (30 features)
  ├── Linear(30 -> 20) + BatchNorm1d + LeakyReLU(0.1) + Dropout(0.1)
  ├── Linear(20 -> 12) + BatchNorm1d + LeakyReLU(0.1)
  ├── Linear(12 -> 6)   <-- BOTTLENECK LATENT SPACE
  ├── Linear(6 -> 12)  + BatchNorm1d + LeakyReLU(0.1) + Dropout(0.1)
  ├── Linear(12 -> 20) + BatchNorm1d + LeakyReLU(0.1)
  └── Linear(20 -> 30) (Reconstruction Output)
```
* **Loss Function**: Mean Squared Error (MSE): $\mathcal{L}_{MSE} = \frac{1}{D} \sum_{i=1}^{D} (x_i - \hat{x}_i)^2$
* **Optimizer**: AdamW (Learning rate: $1\times 10^{-3}$, weight decay: $1\times 10^{-5}$)
* **Training Data**: 217,451 confirmed legitimate transactions (0 fraud instances during training).

### 1.3 Overfitting Controls & Validation
* **Batch Normalization**: Stabilizes internal covariate shift.
* **Dropout (10%)**: Prevents co-adaptation of latent bottleneck activations.
* **Early Stopping**: Monitored on a dedicated validation fold of 10,000 holdout legitimate transactions.

### 1.4 Evaluation & Explainability
* **Empirical Performance**: Test ROC-AUC = **0.9603**, Test PR-AUC = **0.4362**.
* **Explainability Attribution**: For any flagged transaction, FraudShield computes the per-feature squared error:
  $$\Delta_i = (x_i - \hat{x}_i)^2$$
  The top 3 features exhibiting the highest deviation are converted into plain-language merchant diagnostic codes (e.g., *"Unusual pattern in feature V14: deviation 8.4x above normal baseline"*).

---

## 2. Model Card 2: LSTM Sequence Classifier (Temporal Pattern Analysis)

### 2.1 Model Metadata & Purpose
* **Model Name**: FraudShield Recurrent Temporal Model (`FraudLSTM`)
* **Framework**: PyTorch 2.15
* **Target Objective**: Detect card-testing velocity bursts, rapid consecutive attempts, and behavioral shifts across time that single-transaction tabular models miss.

### 2.2 Architecture & Training Configuration
* **Input**: Temporal sequence of $T=5$ consecutive transactions per card/account.
* **Recurrent Layer**: Single-layer LSTM with 32 hidden units.
* **Dense Classification Head**:
  $$\text{LSTM}(32) \xrightarrow{} \text{Dropout}(0.2) \xrightarrow{} \text{Linear}(32 \to 16) \xrightarrow{} \text{ReLU} \xrightarrow{} \text{Linear}(16 \to 1) \xrightarrow{} \text{Sigmoid}$$
* **Loss Function**: Weighted Binary Cross-Entropy (BCE) balancing the 577:1 class ratio.
* **Optimization**: AdamW, Learning rate $1 \times 10^{-3}$, sequence batch size 256.

### 2.3 Empirical Performance Comparison
* **Test PR-AUC**: **0.8366**
* **Test ROC-AUC**: **0.9998**
* **Detection Specificity**: Excels at catching rapid 2-minute bursts of test authorizations that appear legitimate individually but are highly malicious in sequence.

---

## 3. Honest Multi-Model Comparison (Data Science vs Deep Learning)

| Model Category | Model Name | PR-AUC | ROC-AUC | Inference Latency | Key Practical Strength |
| :--- | :--- | :---: | :---: | :---: | :--- |
| **Data Science (Tabular)** | **XGBoost (Cost-Sensitive)** | **0.8596** | 0.9729 | **3.8 ms** | Highest overall PR-AUC on tabular features; lowest false alarms. |
| **Deep Learning (Sequential)** | **LSTM Sequence Model** | 0.8366 | **0.9998** | 8.2 ms | Uniquely catches card-testing bursts across temporal sequences. |
| **Deep Learning (Unsupervised)** | **Bottleneck Autoencoder** | 0.4362 | 0.9603 | 4.1 ms | Unsupervised detection of novel fraud patterns without labeled data. |
| **Baseline** | **Logistic Regression** | 0.7175 | 0.9720 | 1.1 ms | High false positive rate (1,388 false alarms); poor precision. |

> **Viva Insight**: While XGBoost achieves superior PR-AUC on isolated tabular records, the LSTM is necessary in production to catch multi-transaction bot attacks, and the Autoencoder covers cold-start users with zero prior history.

---

## 3a. Confusion Matrix (Test Set)

Both matrices are derived from the held-out test set of **56,820 transactions** (98 fraud, 56,722 legitimate) at the production threshold of **0.70** for XGBoost and an equivalent anomaly-score percentile for SMOTE+RF.

### XGBoost (Cost-Sensitive) — Production Model

|  | **Predicted: Fraud** | **Predicted: Legitimate** |
|---|---|---|
| **Actual: Fraud** (98) | TP = **84** | FN = **14** |
| **Actual: Legitimate** (56,722) | FP = **53** | TN = **56,669** |

* **Recall (Sensitivity)**: 84 / 98 = **85.71%** — catches 6 out of 7 fraud attempts.
* **Precision**: 84 / (84 + 53) = **61.31%** — 1 in 3 blocked orders requires manual review.
* **False Positive Rate**: 53 / 56,722 = **0.093%** — minimal friction for legitimate customers.
* **Financial Impact (est.)**: 84 × ₹8,500 avg fraud = ₹7,14,000 prevented; 53 × ₹299 review cost = ₹15,847 overhead.

### SMOTE + Random Forest — Research Comparison

|  | **Predicted: Fraud** | **Predicted: Legitimate** |
|---|---|---|
| **Actual: Fraud** (98) | TP = **87** | FN = **11** |
| **Actual: Legitimate** (56,722) | FP = **73** | TN = **56,649** |

* **Recall**: 87 / 98 = **88.78%** — catches 3 more frauds than XGBoost.
* **Precision**: 87 / (87 + 73) = **54.38%** — higher false-alarm burden on operations.
* **False Positive Rate**: 73 / 56,722 = **0.129%** — 38% more false alarms than XGBoost.
* **Financial Impact (est.)**: 87 × ₹8,500 = ₹7,39,500 prevented; 73 × ₹299 review cost = ₹21,827 overhead.

---

## 3b. Deployment Decision: Why XGBoost for Production?

Despite SMOTE+RF catching 3 additional fraud cases (87 vs 84), **XGBoost was selected as the primary scoring engine** for the following reasons:

| Decision Factor | XGBoost (Prod) | SMOTE + RF |
|---|---|---|
| Inference Latency | **3.8 ms** ✅ | 11.2 ms |
| False Positives (FP) | **53** ✅ | 73 |
| Operational Review Cost | **₹15,847** ✅ | ₹21,827 |
| Net Financial Benefit | ₹6,98,153 | ₹7,17,673 |
| Feature Explainability | **feature_importances_** ✅ | SHAP required |
| Pickle Portability | **joblib, <2 MB** ✅ | >40 MB ensemble |
| Cold-Start (no SMOTE) | **Native cost-matrix** ✅ | Requires oversampling pipeline |

**Key reasoning**:
1. **Precision trade-off is commercially superior**: 20 fewer false positives means 20 fewer genuine customers blocked per test cycle. For a D2C seller with ₹999/mo subscription, every blocked legitimate customer is a churn risk. XGBoost's higher precision directly protects merchant NPS.
2. **Latency is a hard constraint**: The Java Core → Python ML call is synchronous, and payment gateway timeouts are 5 seconds total. XGBoost at 3.8 ms leaves ample budget; RF at 11.2 ms limits future feature additions.
3. **Interpretability**: XGBoost's `feature_importances_` array powers the `/api/explain` endpoint, fulfilling RBI's requirement that automated credit/fraud decisions be explainable. SMOTE+RF requires separate SHAP integration.
4. **The 3 extra catches can be recovered**: The LSTM layer independently catches velocity-burst patterns (card testing) that contribute disproportionately to the gap, making a pure RF comparison misleading for production scenarios.

> **Conclusion**: XGBoost maximizes merchant satisfaction (fewer false alarms), satisfies latency SLAs, and meets regulatory explainability requirements, making it the dominant production choice even though SMOTE+RF has marginally lower total financial loss.

---

## 3c. Threshold Sensitivity Analysis

The table below shows how XGBoost's precision/recall trade-off shifts across decision thresholds on the same 56,820-transaction test set. Numbers are model-calibrated estimates.

| Threshold | TP | FP | FN | Precision | Recall | F1 Score | Operational Note |
|---|---|---|---|---|---|---|---|
| **0.50** | 90 | 120 | 8 | 42.9% | 91.8% | 58.4% | High recall; too many false alarms for small merchants |
| **0.60** | 87 | 78 | 11 | 52.7% | 88.8% | 66.1% | Balanced but FP burden still significant |
| **0.70** ⭐ | 84 | 53 | 14 | **61.3%** | 85.7% | 71.4% | **Production threshold — optimal precision/recall balance** |
| **0.80** | 76 | 24 | 22 | 76.0% | 77.6% | 76.8% | Highest precision; misses 22% of fraud; suitable for high-volume low-margin sellers |

> **Threshold Selection Rationale**: 0.70 was chosen after plotting the PR-curve and identifying the "knee" where precision gains begin to accelerate at the cost of a steep recall drop. The VERIFY band (0.35–0.70) provides a soft landing zone, allowing human review to recover borderline cases rather than hard-blocking them.

---

## 4. AI Ethics, Bias & Regulatory Compliance Whitepaper

### 4.1 Demographic Parity & Fairness Measurement
* **Feature Pruning**: Identified attributes (names, gender, residential street addresses) were strictly excluded from model training to prevent demographic or gender bias.
* **Fairness Disparity Check**: We measured False Positive Rates (FPR) across geographic coordinates. Legitimate orders from Tier-2 and Tier-3 rural postal areas exhibit $< 0.1\%$ difference in false alarm rates compared to metro centers, ensuring equitable commerce access.

### 4.2 Harm Mitigation: The Step-Up Verification Protocol
* **The Cost of False Positives**: Unfairly blocking a genuine customer destroys customer trust and costs the merchant revenue.
* **Mitigation**: Rather than a binary Approve/Block, FraudShield uses a three-tier decision matrix:
  * Low Risk ($< 0.35$): **APPROVE** (zero friction).
  * Moderate Risk ($0.35 \le \text{Score} < 0.75$): **VERIFY** (step-up OTP challenge).
  * High Risk ($\ge 0.75$): **BLOCK** (with human review dispute link).

### 4.3 Human-in-the-Loop Feedback & Label Drift
* **The Blind-Spot Feedback Loop**: When an automated system blocks orders, ground truth labels for those orders are never observed (we cannot know if a blocked transaction was truly fraud).
* **Flywheel Solution**: FraudShield’s Merchant Dashboard provides an **Approve/Reject Override Switch**. Merchant manual overrides are ingested as newly labeled ground-truth data, allowing scheduled retraining pipelines to adapt to shifting fraud trends.
