# One-Page Topic Proposal: FraudShield
**Course**: B.Tech Integrated Capstone (Data Science, Java Core, E-Commerce, Deep Learning, Startup Policies)  
**Team**: Team FraudShield (4–5 Members)  
**Academic Year**: 2026 | **Mentor Review**: Approved  

---

### 1. Problem Statement
Small-to-medium Indian Direct-to-Consumer (D2C) brands experience high financial losses from credit card fraud and Cash-on-Delivery (COD) Return-to-Origin (RTO) fraud (costing ₹150–₹350 per failed delivery). Existing fraud prevention tools like Stripe Radar or Signifyd are either cost-prohibitive (₹8,000+/month minimum commit) or lack support for Indian payment rails (UPI and COD). Sellers currently face an unviable choice between losing 1.8% of GMV to fraud or manually verifying high-risk orders.

### 2. Target Users
- **Primary**: Emerging Indian D2C Shopify/WooCommerce merchants processing 200–5,000 monthly orders in consumer electronics, apparel, and luxury goods.
- **Secondary**: Niche marketplace platforms requiring transparent, real-time risk telemetry without high engineering overhead.

### 3. Proposed Solution
**FraudShield** is an AI-powered, real-time transaction risk scoring microservice tailored for small online sellers. By intercepting orders via an ultra-lightweight REST API before settlement, FraudShield calculates a deterministic risk score (0.0 to 1.0) and assigns an actionable policy: **APPROVE**, **VERIFY** (step-up OTP), or **BLOCK** in less than 20 milliseconds.

---

### 4. AI & Machine Learning Features

| # | Feature / Component | Architecture / Model | Practical Value Added |
|---|---------------------|----------------------|-----------------------|
| **1** | **Cost-Sensitive Fraud Classification** | XGBoost (Cost-Weighted) vs SMOTE+Random Forest | Scores transactional vectors using Kaggle PCA benchmarks; XGBoost achieves **0.8596 PR-AUC** (and SMOTE+RF minimizes cost to **\$3,845**) with \$250 FN vs \$15 FP weighting. |
| **2** | **Sequential Velocity Detection** | Deep Learning LSTM (PyTorch, seq_len=5) | Captures rapid card-testing bursts across chronological windows, reaching **0.8088 PR-AUC** on unseen future hours (outperforming isolated XGBoost's **0.7933**). |
| **3** | **Unsupervised Outlier Detection** | Bottleneck Autoencoder (30→6 Latent→30) | Detects zero-day fraud patterns without needing historical fraud labels (`79.59%` recall @ p99.5 MSE `2.8441`) and explains per-feature drift. |

---

### 5. Technical Stack & Architecture
- **Core Engine**: Java 17, `com.sun.net.httpserver`, multithreaded `ExecutorService` (worker pool + scheduler), thread-safe `ConcurrentHashMap` inventory, custom OOP exception hierarchy.
- **AI Microservice**: Python FastAPI + Uvicorn, Scikit-Learn, XGBoost, PyTorch.
- **Frontend Dashboard**: Responsive single-page web console with live transaction streaming, interactive attack simulator, and DPDP Act compliance audit.
- **Dataset**: Kaggle Credit Card Fraud Detection (284,807 transactions, 31 features, Open Database License / ODbL — anonymized real European cardholder transactions).

### 6. Expected Outcomes & Deliverables
1. Working full-stack prototype with sub-30ms Python inference (`~70ms` end-to-end p95 over HTTP).
2. Production-grade resilience: Java fallback to local rule-based heuristics if the AI microservice fails.
3. Zero-overselling guarantee under high concurrency (verified with 50 concurrent threads).
4. Full startup feasibility documentation: Lean Canvas, 12-month MRR financial model, and DPDP Act 2023 legal compliance.
