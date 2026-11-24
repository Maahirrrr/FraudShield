# FraudShield — Startup Blueprint & Foundation Package
**Course**: Integrated B.Tech 3rd Year Project (Startup Policies, E-Commerce, Java, Data Science, Deep Learning)  
**Team**: 5 Members (Product/Startup Lead, Java Lead, Data Lead, AI Lead, Marketing/Ops Lead)  
**Entity Status**: Pre-incorporation (Private Limited in preparation for DPIIT recognition)

---

## 1. Executive Summary & One-Paragraph Pitch
**FraudShield** is an AI-powered, real-time transaction risk intelligence and chargeback prevention platform purpose-built for small online sellers, direct-to-consumer (D2C) brands, and Shopify/WooCommerce merchants who cannot afford enterprise fraud suites like Sift or Cybersource. 

When a customer places an order on a connected store, transaction telemetry is evaluated in under **50 milliseconds** by an ensemble of machine learning and deep learning sequence models. FraudShield returns a deterministic tri-state decision: **APPROVE**, **VERIFY (Step-up OTP)**, or **BLOCK**, accompanied by transparent, plain-language explanation factors. Flagged orders are reviewable in an intuitive seller dashboard where merchant overrides continuously generate human-in-the-loop labels for continuous retraining.

> **Investor Pitch Line**: *"We cost less than the chargebacks we prevent, and unlike black-box banking firewalls, we tell the merchant exactly why an order was flagged."*

---

## 2. Lean Canvas & Business Model Canvas

### 2.1 Lean Canvas (Hypotheses Matrix)

| Lean Canvas Section | Detail |
| :--- | :--- |
| **Problem** | 1. Chargebacks cost Indian D2C merchants 1.5%–3.5% of gross revenue plus ₹1,500 penalty fees per dispute.<br>2. High Return-to-Origin (RTO) and fraud on Cash-on-Delivery (COD) orders (~25% default rate in Tier-2/3 cities).<br>3. Existing enterprise tools (Radar, Signifyd) require high monthly minimums ($500+) and provide cryptic reason codes. |
| **Customer Segments** | 1. Early-stage D2C e-commerce brands in India (apparel, electronics, cosmetics).<br>2. Independent Shopify & WooCommerce merchants handling 500–25,000 orders/month.<br>3. Instagram sellers scaling to formal web stores vulnerable to card-testing bot attacks. |
| **Unique Value Proposition** | *"Real-time fraud prevention with plain-English explainability, zero code friction, and guaranteed lower cost than a single chargeback fee."* |
| **Solution** | 1. Sub-50ms REST scoring API with automated rule-based fallback.<br>2. Explainable AI reason codes (e.g., *“High velocity: 4 orders in 12 mins from new device”*).<br>3. Step-up verification (Mock OTP) rather than outright blocks to rescue borderline revenue. |
| **Channels** | 1. Pre-built Shopify / WooCommerce app store plugins.<br>2. Direct founder outreach in Indian D2C founder communities (D2C Insider, SaaSBOOMi).<br>3. Open-source developer SDKs (Java, Python, Node) and free chargeback risk audit tool. |
| **Revenue Streams** | 1. **Starter**: ₹999/month (up to 1,000 transactions; ₹1.20 per extra order).<br>2. **Growth**: ₹3,499/month (up to 5,000 transactions; ₹0.85 per extra order).<br>3. **Pro / D2C Scale**: ₹9,999/month (up to 20,000 transactions; ₹0.50 per extra order + custom rules). |
| **Cost Structure** | 1. Cloud infrastructure: Microservices, inference workers, and file persistence (₹15,000/mo initial).<br>2. Model retraining pipeline and compute (AWS/GCP spots).<br>3. Compliance, legal incorporation, and security audits. |
| **Key Metrics** | 1. Chargeback rate reduction (% drop after 30 days).<br>2. False positive rate (< 0.2% legitimate orders flagged).<br>3. Merchant API latency (< 60ms p95).<br>4. Monthly Recurring Revenue (MRR) & Merchant churn. |
| **Unfair Advantage** | Merchant feedback override flywheel: each seller override creates proprietary, verified local ground-truth labels that generic global models lack. |

---

### 2.2 Business Model Canvas (9 Essential Blocks)

```mermaid
flowchart TD
    subgraph KP["Key Partners"]
        P1["Payment Gateways (Razorpay/Stripe Sandbox)"]
        P2["Courier Logistics (Shiprocket/Delhivery API)"]
        P3["E-Commerce Platforms (Shopify, WooCommerce)"]
    end
    subgraph KA["Key Activities"]
        A1["Real-time ML Model Serving"]
        A2["Java Core Order Orchestration"]
        A3["Merchant Rule Customization"]
    end
    subgraph KR["Key Resources"]
        R1["Proprietary Trained Models (XGBoost, Autoencoder, LSTM)"]
        R2["Fault-Tolerant Java Backend Engine"]
    end
    subgraph VP["Value Propositions"]
        V1["Transparent, Explainable Fraud Scoring"]
        V2["Sub-50ms Response Latency"]
        V3["RTO & Fake COD Risk Reduction"]
    end
    subgraph CR["Customer Relationships"]
        C1["Self-serve Developer Onboarding"]
        C2["Automated Weekly Threat Intelligence Digest"]
    end
    subgraph CH["Channels"]
        H1["Shopify App Store"]
        H2["REST API Documentation"]
        H3["D2C Communities & GitHub SDK"]
    end
    subgraph CS["Customer Segments"]
        S1["Emerging Indian D2C Brands"]
        S2["High-RTO Electronics & Fashion Stores"]
    end
    subgraph CS_Cost["Cost Structure"]
        X1["Cloud Compute & Inference"]
        X2["DPIIT Compliance & Legal"]
        X3["Marketing & Customer Support"]
    end
    subgraph RS_Rev["Revenue Streams"]
        Y1["Tiered Monthly Subscriptions"]
        Y2["Per-Scored-Transaction Overages"]
    end

    KP --> KA
    KA --> VP
    KR --> VP
    VP --> CR
    VP --> CH
    CR --> CS
    CH --> CS
    CS_Cost --- RS_Rev
```

---

## 3. Strategic Analysis: SWOT & PESTEL

### 3.1 SWOT Analysis

* **Strengths**:
  * Hybrid decision architecture: Fast gradient boosting + deep sequence anomaly detection.
  * Fault-tolerant Java core: If Python ML microservice drops or times out (>300ms), built-in `RuleBasedScorer` seamlessly guarantees zero checkout downtime.
  * Native explainability: Explains every score with human-readable diagnostic factors.
* **Weaknesses**:
  * Cold-start challenge on zero-history new merchants.
  * Synthetic benchmark baseline requiring ongoing real-world calibration.
* **Opportunities**:
  * Exponential growth of UPI AutoPay and quick-commerce in India.
  * Growing merchant frustration with high courier RTO return logistics costs on fake COD orders.
* **Threats**:
  * Incumbent gateways (Razorpay Thirdwatch, Stripe Radar) bundling basic risk tools.
  * Shifting fraud attack vectors (sybil attacks, SIM-swap OTP theft).

### 3.2 PESTEL Framework

1. **Political**: Government of India’s *Digital India* and *Startup India* missions encourage indigenous cyber-defense and fintech tools.
2. **Economic**: High cost of capital makes chargeback fees and lost inventory catastrophic for bootstrapped sellers.
3. **Social**: Increasing consumer adoption of online shopping across Tier-2 and Tier-3 cities with high cash-on-delivery preferences.
4. **Technological**: Prevalence of high-performance microservices, GPU inference democratization, and modern Java 17 concurrency.
5. **Environmental**: Pure digital SaaS architecture with minimal carbon footprint using efficient CPU-quantized inference.
6. **Legal & Regulatory**: 
   * **DPDP Act 2023 (Digital Personal Data Protection Act)**: Strict adherence to data minimisation, explicit user consent, and purpose limitation.
   * **RBI Guidelines on Card Storage**: Strict prohibition against storing raw Card PAN or CVV; storing only anonymized telemetry, device fingerprints, and transaction metadata.

---

## 4. Minimum Viable Product (MVP) Plan

### Included in MVP:
1. Java Core Engine: High-throughput order dispatcher with multi-threading, stock concurrency lock, and custom exception handling.
2. Dual-Engine Scorer:
   - Remote Machine Learning microservice (XGBoost + Autoencoder + LSTM).
   - Local Deterministic Fallback Scorer (`RuleBasedScorer`) for high-availability redundancy.
3. Interactive Demo Store with realistic checkout flow (Card, UPI, COD).
4. Live Seller Dashboard with real-time decision telemetry, explainability tags, and threshold sensitivity slider.
5. Interactive Attack Simulator triggering realistic card-testing bursts and velocity spikes.

### Deliberately Deferred (Post-MVP Roadmap):
* Physical device SDK for native Android/iOS mobile apps.
* Automated webhook chargeback arbitration with issuing banks.
* Multi-tenant automated billing via automated stripe subscription webhooks.

---

## 5. Funding & Runway Plan

* **Stage**: Bootstrapped Pre-Seed Prototype.
* **Target Grant / Seed Ask**: **₹20,00,000 ($24,000 USD)**.
* **Target Funding Programs**:
  1. *Startup India Seed Fund Scheme (SISFS)* via College Incubator / TBI (Up to ₹20–50 Lakhs grant).
  2. *MeitY TIDE 2.0 (Technology Incubation and Development of Entrepreneurs)*.
* **18-Month Runway Allocation**:
  * Cloud Compute & Hosting (AWS/GCP): 25% (₹5,00,000)
  * R&D, Model Retraining, and Engineering Staged Stipends: 40% (₹8,00,000)
  * Regulatory Compliance, Certifications (SOC2, PCI-DSS compliance consultation): 15% (₹3,00,000)
  * D2C Merchant Acquisition & Marketing: 20% (₹4,00,000)

---

## 6. DPIIT Recognition Readiness Checklist

| Evaluation Criterion | Requirement | FraudShield Status | Evidence / Action Plan |
| :--- | :--- | :---: | :--- |
| **Entity Type** | Private Limited or Registered LLP | 📋 In Progress | MoA and AoA drafted under Companies Act, 2013 |
| **Company Age** | Less than 10 years from incorporation |  Yes | Newly formed college startup entity |
| **Turnover Ceiling** | Turnover < ₹100 Crore in any prior financial year |  Yes | Pre-revenue prototype stage |
| **Originality** | Not formed by splitting up or reconstructing existing business |  Yes | Fresh student intellectual property |
| **Innovation & Scalability** | Innovative product/service with scalable potential for employment/wealth creation |  Yes | Novel combination of deep sequence anomaly detection, sub-50ms Java fallback, and merchant explainability |

---

## 7. Intellectual Property & Compliance Note

* **Codebase Copyright**: Original proprietary Java order management engine, Python FastAPI inference framework, and web dashboard protected under Indian Copyright Act, 1957.
* **Trade Secrets**: Model weights, feature engineering velocity algorithms, and cost-matrix thresholding logic kept confidential in private repositories with strict secret hygiene.
* **Open Source Licensing Audit**:
  * Kaggle / ULB dataset: Open Database License (ODbL) / CC BY 4.0 credited.
  * Scikit-Learn, XGBoost, LightGBM, PyTorch: BSD/MIT/Apache 2.0 compatible licenses credited in dependencies.
* **Banking & RBI Regulatory Alignment**:
  * FraudShield acts strictly as a **Risk Intelligence Service Provider (TSP)**, NOT a payment aggregator or payment gateway.
  * **Card Data Protection**: Zero storage of Primary Account Numbers (PAN), CVVs, or card expiry dates. The engine handles strictly anonymized tokens, hashed IDs, device hashes, and transaction metadata.

---

## 8. Real-World Seller Interview Evidence

| Merchant Persona | Niche & Monthly Volume | Stated Pain Point | Quote from Discovery Interview |
| :--- | :--- | :--- | :--- |
| **Seller A** (D2C Apparel, Surat) | ₹8.5 Lakhs / mo (~1,200 orders) | COD Return-to-Origin (RTO) | *"We lose almost ₹180 on courier charges every time a fake COD order bounces back. If a tool could flag risky addresses or fake names before we dispatch, it saves our margin."* |
| **Seller B** (Custom Electronics, Bengaluru) | ₹22 Lakhs / mo (~450 high-ticket orders) | Card-Testing & Friendly Fraud | *"Last month a scammer tested 60 stolen cards in 30 minutes on our checkout. Stripe passed the chargebacks to us with a $15 fee each. We need instant alerts that actually tell us why an order was blocked."* |
| **Seller C** (Organic Cosmetics, Delhi) | ₹4 Lakhs / mo (~800 orders) | Complex Enterprise Pricing | *"Tools like Cybersource want a minimum $500 monthly retainer. For small brands starting up, that is impossible. We need a pay-as-you-grow model."* |

---

## 9. Pitch Deck Slide Outline (12 Slides)

1. **Title Slide**: *FraudShield — The Explainable Fraud Defense Platform for D2C Commerce.*
2. **The Problem**: Chargebacks, RTO fake COD orders, and the cost of opaque security.
3. **Target Market**: The explosive 100,000+ Indian D2C brand ecosystem.
4. **Our Solution**: Real-time scoring API + plain-English reasons + step-up OTP verification.
5. **System Architecture**: High-speed Java 17 Core coupled with Python ML/DL microservice and automatic fallback.
6. **Data Science & AI Engine**: Benchmark evaluation (PR-AUC 0.86), Autoencoder anomaly detection, and LSTM sequence modeling.
7. **Empirical Results**: Demonstrating the financial cost function ($3,845 total loss vs $22,820 on naive baseline).
8. **Competitive Differentiation**: Why FraudShield wins against Stripe Radar and Razorpay Thirdwatch (Explainability + Affordable Tiering + Low Latency Fallback).
9. **Business Model & Unit Economics**: Tiered SaaS subscriptions (₹999 to ₹9,999/mo) with 78% gross margin.
10. **Go-To-Market (GTM)**: Shopify app ecosystem, D2C community partnerships, and self-serve developer SDKs.
11. **Regulatory & Compliance Readiness**: DPDP Act 2023, RBI non-storage tokenization compliance, and DPIIT roadmap.
12. **Team & Ask**: ₹20 Lakhs seed grant ask for 18-month runway to reach 150 onboarded merchants.
