# FraudShield — Viva Voce Stress Test Master Guide
**Course**: Integrated B.Tech 3rd Year Project (Viva Preparation & Defense)  
**Standard**: Exhaustive, technical, and commercial answers for all team members.

---

### Q1. Why is accuracy useless here and what did you use instead?
**Answer**:
"In our dataset of 284,807 transactions, only 492 are fraudulent—an extreme class imbalance of **0.173%**. A trivial, 'dumb' classifier that predicts every single transaction is legitimate achieves **99.827% accuracy**, yet it catches **0% of fraud**, making accuracy entirely misleading.
Instead, we evaluated models using:
1. **Precision-Recall AUC (PR-AUC / Average Precision)**: Unlike ROC-AUC, PR-AUC does not get artificially inflated by the overwhelming number of True Negatives (284,315 legitimate transactions); it measures precision strictly across the minority fraud class.
2. **Recall at Fixed Precision**: Measuring the percentage of total fraud captured while keeping false alarms acceptable.
3. **Financial Cost Matrix**: Evaluating total financial loss where a False Negative costs \$250 (chargeback and lost inventory) and a False Positive costs \$15 (customer support friction)."

---

### Q2. How do you know there is no data leakage?
**Answer**:
"We enforced three strict engineering controls against data leakage in `src/preprocess.py`:
1. **Strict Train/Test Split Before Scaling**: We verify models under both **Stratified 80/20 Split** (`stratify=y`, preserving exact 0.173% class ratio across train/test in tabular benchmarking) and **Chronological 80/20 Time Split** (`chronological_split_and_scale` sorted by `Time` for our `FraudLSTM` sequence validation so windows never cross future hours).
2. **Independent Scaler Fitting**: `RobustScaler` on `Amount` and `Time` is fitted **exclusively** on `X_train` (`fit_transform`) and applied to `X_test` (`transform` only).
3. **Resampling Strictly Inside Training Pipelines**: When testing SMOTE oversampling (`sampling_strategy=0.1` + `RandomUnderSampler(sampling_strategy=0.5)`), `imblearn.pipeline.Pipeline` executes resampling strictly within the training split / cross-validation folds, guaranteeing zero synthetic sample contamination in evaluation."

---

### Q3. Your data is synthetic. Why should anyone believe the results?
**Answer**:
"The Kaggle ULB dataset consists of real European credit card transactions transformed via Principal Component Analysis (PCA) for customer privacy. 
To validate that our models do not rely on synthetic artifacts:
1. We evaluated the covariance structure and feature distributions against empirical banking benchmarks.
2. We tested on unseen time slices.
3. In production, we don't rely solely on static training; FraudShield incorporates an active human-in-the-loop merchant override mechanism in the dashboard, continuously generating real-world ground-truth labels from verified local merchant stores."

---

### Q4. Why use deep learning if XGBoost wins on tabular data?
**Answer**:
"While XGBoost achieves our highest tabular PR-AUC (0.8596), it evaluates each transaction as an **independent, isolated record**. 
We incorporated two complementary deep learning architectures to solve specific operational blind spots:
1. **Bottleneck Autoencoder (Unsupervised)**: Trained only on legitimate transactions to catch zero-day attacks and score brand-new customers who have zero historical transaction logs.
2. **LSTM Sequence Model (Sequential)**: Evaluates temporal sequences of transactions ($T=5$) to detect velocity attacks and rapid card-testing bursts that tabular models miss."

---

### Q5. What does the sequence model catch that single-transaction models miss? Show an example.
**Answer**:
"Consider a fraudster conducting **card-testing**: they authorize 4 consecutive transactions of ₹150 across different products within 90 seconds. 
* To an isolated tabular model like XGBoost, each ₹150 transaction looks completely normal (low amount, standard category).
* The **LSTM Sequence Model** processes the temporal vector $(t_1, t_2, t_3, t_4)$. The recurrent hidden state captures the rapid inter-arrival velocity ($\Delta t < 25\text{s}$) and spikes the risk score to 0.89, triggering an automatic BLOCK."

---

### Q6. How do you explain a blocked order to a seller and to the customer?
**Answer**:
* **To the Seller (in Dashboard)**: We provide granular diagnostic telemetry:
  * *'Blocked: Risk Score 0.92. Reasons: (1) High cart value outlier (₹9,500), (2) Severe negative deflection in V14 (-5.5), (3) 4 orders attempted in 15 minutes.'*
* **To the Customer (at Checkout)**: To prevent adversarial reverse-engineering, we never reveal internal AI feature weights. We state:
  * *'Your transaction could not be processed due to a security verification requirement. If you believe this is an error, please click here to verify your identity via OTP or contact customer support.'*"

---

### Q7. What happens when the model service is down, and have you tested it?
**Answer**:
"The Java Core implements the **Fallback Pattern** (`FallbackScorer` wrapping `RemoteModelScorer` and `RuleBasedScorer`) with a bounded **500 ms SLA timeout** (configurable via `-Dmodel.timeout.ms`).
If the Python microservice crashes or stalls beyond the SLA timeout:
1. `RemoteModelScorer` throws `ModelServiceUnavailableException`.
2. `FallbackScorer` catches the exception without crashing checkout and delegates to `RuleBasedScorer` (built on deterministic rules: amounts `>₹8,000` `+0.70`, `>₹3,000` `+0.35`, night hours 23:00–04:00 `+0.25`, 1h velocity `>=4` `+0.45`, anomalous geo coordinates `+0.38`).
3. Orders continue processing seamlessly, marking `fallbackUsed = true` on the dashboard ledger and `/api/metrics`. Verified automatically in JUnit via `FallbackScorerTest` and `RuleAndVelocityFallbackTest`."

---

### Q8. Show the race condition and your fix.
**Answer**:
"In `InventoryService.java`:
* In `decrementStockUnsynchronized()`, multiple threads reading `p.getStock() >= quantity` concurrently can see stock available before any thread has decremented it, causing **overselling** (e.g. 50 threads buying 10 units of limited inventory).
* In `decrementStockSynchronized()`, we use Java's `synchronized` keyword (and `ConcurrentHashMap`) to make the stock check and decrement an **atomic operation**.
* In our test `InventoryConcurrencyTest.java`, 50 threads strike simultaneously: exactly 10 succeed, 40 throw `OutOfStockException`, and the final stock is verified at exactly 0."

---

### Q9. Who pays you, how much, and why not Stripe Radar or a gateway's built-in tool?
**Answer**:
* **Customers**: Indian D2C brands and Shopify/WooCommerce sellers processing 500–25,000 orders/month.
* **Pricing**: Tiered SaaS from ₹999/mo (Starter) to ₹9,999/mo (Pro) + ₹0.50–₹1.20 per transaction overage.
* **Why Not Stripe Radar / Razorpay Thirdwatch?**
  1. **Cost Accessibility**: Stripe Radar requires enterprise accounts or flat gateway lock-in; we are gateway-agnostic (works with Razorpay, Cashfree, Stripe, and COD).
  2. **RTO & COD Protection**: Enterprise US tools do not solve Indian Cash-on-Delivery RTO fraud, which accounts for 60%+ of tier-2/3 Indian orders.
  3. **Explainability**: Gateways offer opaque 'Risk Level: High'; FraudShield provides actionable plain-English reason codes."

---

### Q10. What personal data do you hold and under what consent?
**Answer**:
"Under India's **DPDP Act 2023** and **RBI Card Tokenization Rules**:
1. We hold **ZERO raw credit card PANs, CVVs, or bank passwords**. All card payments are simulated via sandbox tokens.
2. We hold only anonymized transaction telemetry: order ID, amount, timestamp, anonymized device fingerprint, and coordinate deltas.
3. Users provide explicit consent via an un-ticked checkout checkbox: *'I consent to transaction telemetry analysis for anti-fraud protection under DPDP Act 2023.'*"

---

### Q11. How do you choose the threshold, and what does a false positive cost?
**Answer**:
"We calibrated the decision thresholds using an empirical business **Financial Cost Function**:
$$\text{Expected Loss} = (\text{False Negatives} \times \$250) + (\text{False Positives} \times \$15)$$
* Missed Fraud (FN) costs \$250 in chargeback fines, merchant processing penalties, and lost physical inventory.
* False Alarms (FP) cost \$15 in customer service inquiry time.
Using our Precision-Recall curve, we established a **tri-state policy**:
* Score $< 0.35 \implies$ **APPROVE**
* $0.35 \le \text{Score} < 0.70 \implies$ **VERIFY (Step-Up OTP)** (rescues legitimate customers without outright blocking them!)
* Score $\ge 0.70 \implies$ **BLOCK**."

---

### Q12. What did not work, and what would you do differently?
**Answer**:
"1. **What Didn't Work**: Naive Logistic Regression with standard class weights produced **1,388 false positives** on our test set, resulting in an unacceptable \$22,820 financial loss. Simple linear decision boundaries cannot separate high-dimensional PCA transaction spaces.
2. **What Didn't Work**: Training the Autoencoder with a large bottleneck (latent size 16) caused it to easily reconstruct fraud anomalies too, destroying anomaly detection power; reducing the bottleneck to 6 dimensions was critical.
3. **What We Would Do Differently**: Given more time, we would build a native Shopify app extension for 1-click merchant onboarding, and train a Graph Neural Network (GNN) to link fraudsters sharing common device fingerprints across multiple merchant stores."

---

### Q13. Why did you choose XGBoost as primary tabular scorer alongside Deep Learning models?
**Answer**:
"Rather than an either/or choice, FraudShield runs all three engines live in `model-service/app.py` for distinct mathematical regimes:

1. **Tabular Feature Discrimination**: Consistent with Shwartz-Ziv & Armon (2022) and Grinsztajn et al. (NeurIPS 2022), gradient-boosted trees dominate structured tabular data at this scale: our cost-sensitive XGBoost achieves **0.8596 PR-AUC** on stratified holdout.
2. **Real-Time Latency**: Combined XGBoost + PyTorch Autoencoder + PyTorch LSTM forward inference runs in **6–30 ms** on CPU per request (`latency_ms` reported live in `/score`), well inside payment gateway timeouts.
3. **Explainability**: XGBoost `feature_importances_` (via `GET /api/explain`) combined with per-feature Autoencoder reconstruction residuals $\Delta_i = (x_i - \hat{x}_i)^2$ gives auditable diagnostic reasons for merchants.
4. **Why DL Adds Real Measured Value**: Under strict chronological future-hour splitting, our **PyTorch `FraudLSTM` (`seq_len=5`) achieves PR-AUC 0.8088 vs XGBoost's 0.7933** (`$4,435` vs `$4,770` loss), proving that temporal modeling captures burst behavior that isolated rows miss, while `FraudAutoencoder` flags `79.59%` of frauds without any fraud training labels."

---

### Q14. Explain the SMOTE algorithm and how you prevented resampling leakage.
**Answer**:
"SMOTE (**Synthetic Minority Over-Sampling Technique**, Chawla et al., 2002) mitigates extreme `577:1` class imbalance (`492` frauds out of `284,807` transactions):

1. For each minority sample $x$, locate its $k=5$ nearest minority neighbors.
2. Interpolate along the connecting line segment: $x_{\text{syn}} = x + \lambda(x_{\text{nbr}} - x)$ for $\lambda \sim U(0,1)$.
3. In `src/models.py`, we combine `SMOTE(sampling_strategy=0.1)` (raising minority ratio to 10%) with `RandomUnderSampler(sampling_strategy=0.5)` inside an **`imblearn.pipeline.Pipeline`** paired with `RandomForestClassifier(n_estimators=100, max_depth=10)`.

Wrapping sampling in `ImbPipeline` guarantees synthetic points are generated **strictly inside training folds**, leaving test data 100% untouched. In our stratified benchmark, `SMOTE + RF` reached the highest Recall (**88.78%**) and lowest total financial cost (**\$3,845**), though with **73 False Positives** vs XGBoost's **53**, which is why XGBoost leads primary PR-AUC (`0.8596`)."

---

### Q15. What is the difference between ROC-AUC and PR-AUC? Why is PR-AUC better for imbalanced data?
**Answer**:
"Both metrics summarise classifier performance across all decision thresholds, but they measure fundamentally different things.

**ROC-AUC (Receiver Operating Characteristic — Area Under Curve)**:
- Plots **True Positive Rate (Recall)** on the Y-axis against **False Positive Rate** (FPR = FP / (FP + TN)) on the X-axis.
- AUC = probability that the model ranks a random positive higher than a random negative.
- **The imbalance problem**: In our dataset, True Negatives (TN) = ~284,315. The FPR denominator (FP + TN) is dominated by this enormous TN count. Even a classifier generating 300 false positives shows an FPR of only 300/284,315 = **0.001**, making it appear excellent. This masks real-world false alarm damage.

**PR-AUC (Precision-Recall — Area Under Curve)**:
- Plots **Precision** (TP / (TP + FP)) on the Y-axis against **Recall** (TP / (TP + FN)) on the X-axis.
- Crucially, the TN count **does not appear in any PR-AUC formula**. Both Precision and Recall are computed purely from the minority (fraud) class perspective.
- When a classifier generates excessive false positives, Precision drops sharply, penalising the curve. This directly reflects the merchant's experience: every false positive is a legitimate customer wrongly blocked.

**Concrete Example From Our Benchmark**:
| Model | ROC-AUC | PR-AUC | Financial Loss |
|---|---|---|---|
| Logistic Regression | 0.9720 | 0.7175 | $22,820 |
| XGBoost | 0.9729 | **0.8596** | $4,295 |

Both show similar ROC-AUC (~0.97), but PR-AUC clearly separates them — consistent with the actual financial loss difference of $18,525. **PR-AUC is the honest metric for fraud detection.**"

---

### Q16. How does your Java multithreading prevent race conditions in inventory management?
**Answer**:
"The race condition arises in `InventoryService.java` when multiple threads execute the **check-then-act** sequence concurrently:

```java
// UNSAFE — Race Condition Present
public void decrementStockUnsynchronized(String productId, int qty) {
    Product p = inventory.get(productId);
    if (p.getStock() >= qty) {       // Thread A and Thread B both read stock=10 here
        p.setStock(p.getStock() - qty); // Both decrement — stock goes to 0 then -qty
    }
}
```

If 50 threads read `stock = 10` before any thread has written the decrement, **50 threads can all pass the `>= qty` check** simultaneously, causing overselling.

**Our Fix — Three-Layer Concurrency Defense**:

1. **`synchronized` Method**: The critical section (check + decrement) is wrapped in a `synchronized` block on the product object, making it an **atomic operation**. Only one thread can hold the monitor lock at a time.
```java
public synchronized boolean decrementStockSynchronized(String productId, int quantity) throws OutOfStockException {
    Product p = products.get(productId);
    if (p == null) return false;
    if (p.getStock() >= quantity) {
        p.setStock(p.getStock() - quantity);
        return true;
    }
    throw new OutOfStockException(productId, quantity, p.getStock());
}
```

2. **`ConcurrentHashMap`**: `InventoryService` uses `ConcurrentHashMap<String, Product>` for lock-free reads and safe concurrent registration.

3. **`ExecutorService` with `BlockingQueue<Transaction>`**: `OrderService` offloads asynchronous CSV audit exports to a bounded `LinkedBlockingQueue(1000)` processed by worker threads while maintaining concurrent 1-hour sliding velocity windows per customer (`ConcurrentHashMap<String, Deque<Instant>>`).

**Proof from JUnit Test `InventoryConcurrencyTest.java`**: 50 threads fire simultaneously via `CountDownLatch` targeting 10 units. Exactly **10 succeed**, **40 throw `OutOfStockException`**, and final stock is verified as **0**."

---

### Q17. What is the DPDP Act 2023 and how does your system comply?
**Answer**:
"The **Digital Personal Data Protection Act 2023** is India's first comprehensive data protection legislation, passed in August 2023. It governs how 'data fiduciaries' (entities that determine the purpose and means of data processing) must collect, store, and delete personal data of Indian residents.

**Key Provisions and FraudShield's Compliance**:

| DPDP Provision | Requirement | FraudShield Implementation |
|---|---|---|
| **Section 6 — Consent** | Explicit, informed, specific consent before processing | Un-ticked checkbox at checkout: *'I consent to transaction telemetry analysis for anti-fraud purposes under DPDP Act 2023'* |
| **Section 5 — Purpose Limitation** | Data used only for the consented purpose | Telemetry used exclusively for fraud scoring; never sold, shared with advertisers, or used for profiling |
| **Section 8 — Data Minimisation** | Collect only what is necessary | Zero PII collected: no name, email, phone, full address, or card data — only anonymised hashes and coordinates |
| **Section 12 — Right to Erasure** | Data principal can request deletion | Programmatic `DELETE /api/data/erase?order_id=<uuid>` API — executes within 72 hours |
| **Section 9 — Data Retention** | Retain only as long as necessary | Automated 90-day rolling deletion cron job on `transactions.jsonl` |
| **Section 11 — Security** | Reasonable security safeguards | HMAC-SHA256 authenticated API requests; JSONL stored locally; no cloud upload without encryption |

**RBI Card Tokenization Compliance**: FraudShield never stores Primary Account Numbers (PAN), CVVs, or card expiry dates. All card transactions use Razorpay/Stripe sandbox tokens — raw card data never touches our servers.

**Key Distinction**: FraudShield is a **Risk Intelligence Service Provider (TSP)**, not a Payment Aggregator or Payment Gateway. This classification exempts us from RBI's payment aggregator licensing requirements while still obligating us to DPDP Act compliance."

---

### Q18. If your Python microservice goes down, what happens? Walk through the fallback chain.
**Answer**:
"This is the **most critical resilience feature** of FraudShield. We designed the entire fallback chain so that **the checkout experience never breaks**, even if the entire ML stack crashes. Here is the exact sequence:

**Timeline: Python Microservice Becomes Unavailable**

```
T+0ms:   Checkout submits order -> Java receives POST /api/orders
T+1ms:   OrderService validates consent + deducts stock atomically + computes 1h velocity
T+2ms:   RemoteModelScorer opens HTTP request to http://localhost:8000/score (500ms timeout)
T+500ms: Connection refused or SLA timeout expires -> throws ModelServiceUnavailableException
T+501ms: FallbackScorer catches exception cleanly and delegates to RuleBasedScorer:
         - Baseline: 0.05
         - Amount > ₹8,000 (+0.70) or > ₹3,000 (+0.35)
         - Night window 23:00–04:00 (+0.25)
         - 1h velocity >= 4 (+0.45)
         - Extreme coordinates |lat|>60 or |lon|>130 (+0.38)
         -> Clamped to [0.0, 1.0], mapped to APPROVE (<0.35) / VERIFY / BLOCK (>=0.70)
T+502ms: Java persists JSONL, queues CSV audit, returns valid decision with fallback_used=true
```

**What We Tested in `FallbackScorerTest.java`**: We point `RemoteModelScorer` to `localhost:9999` (unreachable). The test verifies:
- The order receives a valid APPROVE/VERIFY/BLOCK decision (not an error/exception propagated to the store).
- `result.isFallbackUsed() == true`.
- End-to-end latency remains under 50ms (rule engine is O(1) arithmetic).

**Production Enhancement** (beyond MVP): The Java service would emit a `SERVICE_DEGRADED` metric to an alerting system (Prometheus/Grafana) so the team is paged within 60 seconds of the ML service going offline."

---

### Q19. What is your Unit Economics? What is Customer Acquisition Cost and LTV?
**Answer**:
"Unit economics is the financial analysis of the costs and revenues directly attributable to acquiring and serving **one customer unit** — in our case, one merchant.

**Customer Acquisition Cost (CAC)**:
> CAC = Total Sales & Marketing Spend ÷ Number of New Customers Acquired

- Monthly marketing budget: ₹25,000 (content creation, LinkedIn ads, D2C community events).
- Projected new merchants per month (Month 4–6): ~5 net new merchants.
- **CAC = ₹25,000 ÷ 5 = ₹5,000 per merchant** (blended, including trial conversions).
- By Month 10+ with referral flywheel, CAC drops to estimated **₹1,800** as word-of-mouth dominates acquisition.

**Cost to Serve (COGS per Merchant per Month)**:
| Cost Component | Monthly Cost |
|---|---|
| Cloud compute (AWS Graviton CPU inference) | ₹350 |
| File storage & JSONL backups | ₹60 |
| OTP SMS gateway (2% step-up × ₹0.20) | ₹20 |
| **Total COGS** | **₹430** |

**Average Revenue Per Merchant (ARPM)**: ₹3,500/month (blended across Starter, Growth, Pro plans).

**Gross Margin Per Merchant**: (₹3,500 − ₹430) ÷ ₹3,500 = **87.7%** — exceptional for a SaaS business (industry benchmark: 70–80%).

**Customer Lifetime Value (LTV)**:
> LTV = ARPM × Gross Margin × Average Customer Lifetime

- Average retention: 10 months (based on comparable Indian SaaS SMB benchmarks).
- **LTV = ₹3,500 × 87.7% × 10 = ₹30,695** (~₹31,000 per merchant).

**LTV / CAC Ratio**:
- At mature CAC of ₹1,800: **LTV/CAC = ₹31,000 ÷ ₹1,800 = 17.2×** — extremely healthy (SaaS benchmark: >3× is viable, >5× is excellent).
- **Payback Period = CAC ÷ Monthly Net Contribution = ₹1,800 ÷ ₹3,070 = 0.6 months** — customer pays back acquisition cost in under 3 weeks.

**Break-Even Point**: Fixed monthly cloud overhead ₹18,000 ÷ ₹3,070 net contribution = **5.86 → 6 active merchants**. We model 6 merchants from Month 1."

---

### Q20. How would you scale FraudShield from 70 to 7,000 merchants?
**Answer**:
"Scaling 100× requires addressing three distinct bottlenecks: **inference throughput, data persistence, and multi-tenancy isolation**. Here is our staged scaling roadmap:

**Phase 1: 70 → 500 Merchants (Vertical Scaling + Async)**
- Replace synchronous Python Uvicorn with **async FastAPI + Uvicorn with 4 workers** (already supported — just change `--workers 4`).
- Introduce a **Redis cache** for repeat device fingerprint scores (TTL: 60 seconds) — eliminates re-scoring of identical repeat requests.
- Move `transactions.jsonl` file append to a **PostgreSQL database** with connection pooling (pgBouncer) to handle concurrent writes from multiple Java threads safely.
- Deploy on **AWS Graviton3 `c7g.xlarge`** (4 vCPU, 8GB RAM) — cost: ~₹6,000/month; handles ~2,000 requests/second.

**Phase 2: 500 → 2,000 Merchants (Horizontal Scaling + Message Queue)**
- Introduce **Kafka or AWS SQS** as an order event queue: Java publishes order events; multiple Python consumer workers subscribe and score in parallel — decouples throughput from latency.
- Deploy Python microservice as **Docker containers on Kubernetes (AWS EKS)** with Horizontal Pod Autoscaler (HPA) triggering scale-out when CPU > 70%.
- Implement **multi-tenant data isolation**: each merchant gets a dedicated JSONL partition / PostgreSQL schema; no cross-merchant data bleed.
- Replace file-based model loading with **MLflow Model Registry** — enables A/B model rollouts per merchant segment without downtime.

**Phase 3: 2,000 → 7,000 Merchants (Global Distribution + Real-time Retraining)**
- Deploy across **3 AWS regions** (Mumbai, Singapore, Frankfurt) with Route 53 latency-based routing — ensures sub-50ms SLA for merchants across time zones.
- Build a **streaming retraining pipeline**: merchant override labels → Kafka topic → Spark Structured Streaming → incremental XGBoost model updates published every 24 hours.
- Introduce **merchant-specific model fine-tuning**: the global XGBoost model is fine-tuned per merchant cluster (fashion vs electronics vs cosmetics) using transfer learning — improves precision on niche fraud patterns.
- Migrate billing to **Stripe Subscriptions / Razorpay Subscriptions API** for automated MRR management at scale.

**Cost at 7,000 Merchants**:
- Estimated cloud COGS at scale: ₹200–₹300/merchant/month (economies of scale on compute).
- Gross margin improves to **92%+** — SaaS scaling law holds.

**The key insight**: Our Java Core and Python microservice are already stateless and horizontally scalable by design. The main architectural investment is in the data layer (from JSONL files to Kafka + PostgreSQL + object storage) and the multi-tenancy isolation layer."
