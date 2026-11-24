package com.fraudshield.model;

import java.time.Instant;
import java.util.List;

public class Transaction {
    private final String id;
    private final String orderId;
    private final String merchantId;
    private final String paymentMethod;
    private final double amount;
    private final double riskScore;
    private final Decision decision;
    private final List<String> reasons;
    private final boolean fallbackUsed;
    private final Instant timestamp;

    public Transaction(String id, String orderId, String merchantId, String paymentMethod,
                       double amount, double riskScore, Decision decision,
                       List<String> reasons, boolean fallbackUsed) {
        this.id = id;
        this.orderId = orderId;
        this.merchantId = merchantId;
        this.paymentMethod = paymentMethod;
        this.amount = amount;
        this.riskScore = riskScore;
        this.decision = decision;
        this.reasons = reasons;
        this.fallbackUsed = fallbackUsed;
        this.timestamp = Instant.now();
    }

    public String getId() { return id; }
    public String getOrderId() { return orderId; }
    public String getMerchantId() { return merchantId; }
    public String getPaymentMethod() { return paymentMethod; }
    public double getAmount() { return amount; }
    public double getRiskScore() { return riskScore; }
    public Decision getDecision() { return decision; }
    public List<String> getReasons() { return reasons; }
    public boolean isFallbackUsed() { return fallbackUsed; }
    public Instant getTimestamp() { return timestamp; }
}
