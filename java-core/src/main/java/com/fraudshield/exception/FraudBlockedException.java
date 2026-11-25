package com.fraudshield.exception;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class FraudBlockedException extends Exception {
    private final String transactionId;
    private final String orderId;
    private final double riskScore;
    private final List<String> reasons;
    private final boolean fallbackUsed;
    private final double latencyMs;
    private final Map<String, Object> engineBreakdown;

    public FraudBlockedException(String message, double riskScore, List<String> reasons) {
        this(message, "", "", riskScore, reasons, false, 0.0, Collections.emptyMap());
    }

    public FraudBlockedException(String message, String transactionId, String orderId,
                                 double riskScore, List<String> reasons, boolean fallbackUsed,
                                 double latencyMs, Map<String, Object> engineBreakdown) {
        super(message);
        this.transactionId = transactionId;
        this.orderId = orderId;
        this.riskScore = riskScore;
        this.reasons = reasons != null ? reasons : Collections.emptyList();
        this.fallbackUsed = fallbackUsed;
        this.latencyMs = latencyMs;
        this.engineBreakdown = engineBreakdown != null ? engineBreakdown : Collections.emptyMap();
    }

    public String getTransactionId() { return transactionId; }
    public String getOrderId() { return orderId; }
    public double getRiskScore() { return riskScore; }
    public List<String> getReasons() { return reasons; }
    public boolean isFallbackUsed() { return fallbackUsed; }
    public double getLatencyMs() { return latencyMs; }
    public Map<String, Object> getEngineBreakdown() { return engineBreakdown; }
}
