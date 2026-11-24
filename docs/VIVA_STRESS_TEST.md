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
"We enforced three strict engineering controls against data leakage:
1. **Time-Stratified Splitting**: We split transactions chronologically rather than with random shuffling, ensuring models never peek into future transaction trends.
2. **Independent Scaler Fitting**: `RobustScaler` on `Amount` and `Time` was fit **exclusively** on the training partition (`X_train.fit()`), and subsequently applied to transform `X_test` without refitting.
3. **Resampling Inside Cross-Validation Folds**: When testing SMOTE oversampling, we used `imblearn.pipeline.Pipeline`, which applies synthetic sampling strictly inside training cross-validation folds, guaranteeing zero synthetic sample contamination in the validation folds."

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
"The Java Core implements the **Fallback Pattern** (`FallbackScorer` wrapping `RemoteModelScorer` and `RuleBasedScorer`) with a strict **300 ms SLA timeout**.
If the Python microservice crashes or takes longer than 300 ms:
1. `RemoteModelScorer` throws `ModelServiceUnavailableException`.
2. `FallbackScorer` catches the exception without crashing the checkout and immediately delegates to `RuleBasedScorer` (built on our EDA findings: amounts $>₹8,000$, night hours 11 PM–4 AM, location discrepancies).
3. The store continues processing orders seamlessly, marking `fallback_used = true` on the merchant dashboard. We proved this in JUnit via `FallbackScorerTest`!"

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
