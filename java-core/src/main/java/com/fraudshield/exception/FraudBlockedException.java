package com.fraudshield.exception;

import java.util.List;

public class FraudBlockedException extends Exception {
    private final double riskScore;
    private final List<String> reasons;

    public FraudBlockedException(String message, double riskScore, List<String> reasons) {
        super(message);
        this.riskScore = riskScore;
        this.reasons = reasons;
    }

    public double getRiskScore() { return riskScore; }
    public List<String> getReasons() { return reasons; }
}
