package com.fraudshield.model;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class Transaction {
    private final String id;
    private final String orderId;
    private final String merchantId;
    private final String paymentMethod;
    private final double amount;
    private final double riskScore;
    private Decision decision;
    private final List<String> reasons;
    private final boolean fallbackUsed;
    private final double latencyMs;
    private final Map<String, Object> engineBreakdown;
    private final Instant timestamp;

    public Transaction(String id, String orderId, String merchantId, String paymentMethod,
                       double amount, double riskScore, Decision decision,
                       List<String> reasons, boolean fallbackUsed) {
        this(id, orderId, merchantId, paymentMethod, amount, riskScore, decision,
                reasons, fallbackUsed, 0.0, Collections.emptyMap());
    }

    public Transaction(String id, String orderId, String merchantId, String paymentMethod,
                       double amount, double riskScore, Decision decision,
                       List<String> reasons, boolean fallbackUsed,
                       double latencyMs, Map<String, Object> engineBreakdown) {
        this.id = id;
        this.orderId = orderId;
        this.merchantId = merchantId;
        this.paymentMethod = paymentMethod;
        this.amount = amount;
        this.riskScore = riskScore;
        this.decision = decision;
        this.reasons = reasons;
        this.fallbackUsed = fallbackUsed;
        this.latencyMs = latencyMs;
        this.engineBreakdown = engineBreakdown != null ? engineBreakdown : Collections.emptyMap();
        this.timestamp = Instant.now();
    }

    public String getId() { return id; }
    public String getOrderId() { return orderId; }
    public String getMerchantId() { return merchantId; }
    public String getPaymentMethod() { return paymentMethod; }
    public double getAmount() { return amount; }
    public double getRiskScore() { return riskScore; }
    public Decision getDecision() { return decision; }
    public void setDecision(Decision decision) { this.decision = decision; }
    public List<String> getReasons() { return reasons; }
    public boolean isFallbackUsed() { return fallbackUsed; }
    public double getLatencyMs() { return latencyMs; }
    public Map<String, Object> getEngineBreakdown() { return engineBreakdown; }
    public Instant getTimestamp() { return timestamp; }
}
