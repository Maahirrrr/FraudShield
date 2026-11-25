# FraudShield — AI Transaction Risk Intelligence & Anti-Fraud Platform
**Integrated 3rd Year B.Tech Academic Capstone Project**  
**Subjects Combined**: Java Core · E-Commerce · Data Science · Deep Learning · Startup Policies  
**Repository**: `/home/maahir/.gemini/antigravity/scratch/credit_card_fraud_detection`

---

## Project Overview & 100-Mark Rubric Coverage

FraudShield is a real-time fraud scoring and inventory-safe e-commerce checkout platform designed for emerging Indian D2C brands. A multithreaded Java 17 core engine orchestrates atomic inventory locking, DPDP Act 2023 consent/erasure compliance, and REST APIs, dispatching real-time risk scoring to a hybrid Python AI microservice running XGBoost + PyTorch Autoencoder + PyTorch LSTM, backed by a deterministic Java fallback engine.

| Subject Component | Marks | Key Implementations & Verified Artifacts | Location |
| :--- | :---: | :--- | :--- |
| **Startup Policies** | **10** | Lean Canvas, Business Model Canvas (9 blocks), SWOT, PESTEL, MVP plan, ₹20L SISFS funding plan, 12-month MRR forecast, DPIIT readiness checklist, competitive analysis, 12-slide pitch outline | [`docs/STARTUP_BRIEF.md`](file:///home/maahir/.gemini/antigravity/scratch/credit_card_fraud_detection/docs/STARTUP_BRIEF.md) |
| **E-Commerce & Legal** | **10** | 3-tier SaaS revenue model (87.7% gross margin), payment gateway sandbox comparison, COD RTO 5-step protocol, DPDP Act 2023 data flow, active Right-to-Erasure API (`DELETE /api/data/erase`), un-ticked explicit consent | [`docs/ECOMMERCE_DESIGN.md`](file:///home/maahir/.gemini/antigravity/scratch/credit_card_fraud_detection/docs/ECOMMERCE_DESIGN.md) |
| **Java Core Engine** | **20** | Framework-free Java 17 HTTP server (`com.sun.net.httpserver`), `Payment` polymorphism (`Card`, `UPI`, `COD`), `FraudScorer` fallback composition, 6 custom exceptions, `BlockingQueue` + `ExecutorService` async CSV pipeline, `synchronized` race-condition protection, 5 passing JUnit 5 tests | [`java-core/`](file:///home/maahir/.gemini/antigravity/scratch/credit_card_fraud_detection/java-core/) |
| **Data Science** | **15** | 284,807 Kaggle ULB transactions, zero-leakage `RobustScaler` (train-only fit), Pearson correlation analysis (`V17` -0.3265, `V14` -0.3025, `V12` -0.2606), stratified + chronological benchmarks, exact cost matrix (`$250 FN` / `$15 FP`) & threshold sensitivity script | [`src/`](file:///home/maahir/.gemini/antigravity/scratch/credit_card_fraud_detection/src/), [`notebooks/`](file:///home/maahir/.gemini/antigravity/scratch/credit_card_fraud_detection/notebooks/) |
| **Deep Learning** | **20** | **PyTorch Bottleneck Autoencoder** (`30→6→30`, unsupervised zero-day MSE + per-feature residual attribution) & **PyTorch FraudLSTM** (`seq_len=5`, chronological sliding windows, calibrated `BCEWithLogitsLoss`, beats tabular XGBoost on future-hour chronological holdout) | [`deep-learning/`](file:///home/maahir/.gemini/antigravity/scratch/credit_card_fraud_detection/deep-learning/) |
| **Integration & Quality** | **10** | Live hybrid 3-engine Python microservice (`app.py`), FX parity scaling (`₹85/unit`), live per-customer sequence windowing, 500ms SLA timeout with automatic `RuleBasedScorer` fallback | [`model-service/`](file:///home/maahir/.gemini/antigravity/scratch/credit_card_fraud_detection/model-service/) |
| **UI & Live Console** | **15** | Tasteskill-compliant Framed Sheet enterprise console (`web/index.html` + `charts.js`), live transaction inspection drawer, real Checkout store wired to Java inventory & OTP APIs, adversarial attack simulator, diagnostic plot viewer, 20-question viva defense guide | [`web/`](file:///home/maahir/.gemini/antigravity/scratch/credit_card_fraud_detection/web/), [`docs/VIVA_STRESS_TEST.md`](file:///home/maahir/.gemini/antigravity/scratch/credit_card_fraud_detection/docs/VIVA_STRESS_TEST.md) |

---

## Architecture & Live Data Flow

```text
 [ Web Console & Demo Store ] (Port 5000 · web/index.html + charts.js)
              │
     HTTP POST /api/orders · GET /api/metrics · GET /api/inventory · DELETE /api/data/erase
              ▼
   [ Java 17 Core Engine ] (Port 8085 · java-core)
      ├── InventoryService    (ConcurrentHashMap + synchronized atomic decrement)
      ├── OrderService        (1h per-customer velocity tracker + BlockingQueue async CSV audit)
      ├── FallbackScorer      (Composition: RemoteModelScorer ──timeout/err──▶ RuleBasedScorer)
      └── FileStore           (ReentrantLock JSONL + CSV export + DPDP Section 12 erasure)
              │
     HTTP POST /score (with IP, geo coords, 1h velocity)
              ▼
 [ Python FastAPI AI Engine ] (Port 8000 · model-service/app.py)
      ├── XGBoost Classifier         (Supervised cost-sensitive tabular score)
      ├── PyTorch Bottleneck AE      (Unsupervised reconstruction MSE + top-3 feature residuals)
      └── PyTorch FraudLSTM          (Per-customer 5-step rolling window sequence burst detection)
```

---

## Empirical Model Benchmarks

### 1. Stratified Holdout (`N = 56,962`, 98 frauds · `reports/benchmark_results.csv`)
| Model | PR-AUC (Primary) | ROC-AUC | Recall | Precision | F1-Score | TP | FP | FN | Financial Loss (\$)* |
| :--- | :---: | :---: | :---: | :---: | :---: | :-: | :-: | :-: | :---: |
| **XGBoost (Cost-Sensitive)** | **0.8596** | 0.9729 | 85.71% | 61.31% | 0.7149 | 84 | 53 | 14 | \$4,295 |
| **LightGBM (Balanced)** | 0.8474 | 0.9770 | 84.69% | 63.85% | 0.7281 | 83 | 47 | 15 | \$4,455 |
| **SMOTE + Random Forest** | 0.8410 | **0.9793** | 88.78% | 54.38% | 0.6744 | 87 | 73 | 11 | **\$3,845** |
| **Random Forest (Balanced)** | 0.8389 | 0.9765 | 82.65% | **71.05%** | **0.7642** | 81 | 33 | 17 | \$4,745 |
| **Logistic Regression (Balanced)** | 0.7175 | 0.9720 | **91.84%** | 6.09% | 0.1142 | 90 | 1,388 | 8 | \$22,820 |
| **Bottleneck Autoencoder (`@p99.5`)** | 0.4598 | 0.9536 | 79.59% | 20.21% | 0.3223 | 78 | 308 | 20 | \$9,620 |

### 2. Strict Chronological Holdout (`Time` 80/20 split · `reports/dl_benchmark_results.csv`)
| Model | PR-AUC | ROC-AUC | Recall | Precision | F1-Score | TP | FP | FN | Financial Loss (\$)* |
| :--- | :---: | :---: | :---: | :---: | :---: | :-: | :-: | :-: | :---: |
| **PyTorch FraudLSTM (`seq_len=5`)** | **0.8088** | **0.9856** | **78.67%** | 67.05% | 0.7239 | 59 | 29 | 16 | **\$4,435** |
| **XGBoost (Tabular Single-Row)** | 0.7933 | 0.9838 | 76.00% | **76.00%** | **0.7600** | 57 | 18 | 18 | \$4,770 |

*\*Cost formula: $(\text{FN} \times \$250) + (\text{FP} \times \$15)$.*

---

## Quick Start & Tests

```bash
cd /home/maahir/.gemini/antigravity/scratch/credit_card_fraud_detection

# Start Python AI (:8000) + Java Core (:8085) + Web Console (:5000)
./.venv/bin/python run_fraudshield.py

# Run Java JUnit 5 suite (5 tests covering concurrency, fallback, velocity/geo rules, DPDP erasure)
cd java-core && mvn clean test
```
