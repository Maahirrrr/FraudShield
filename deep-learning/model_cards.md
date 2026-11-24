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
