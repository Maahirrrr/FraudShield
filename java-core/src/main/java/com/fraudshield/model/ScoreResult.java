package com.fraudshield.model;

import java.util.Collections;
import java.util.List;

/**
 * Encapsulates the fraud score, final decision, plain-language reasons,
 * and whether fallback was used.
 */
public class ScoreResult {
    private final double riskScore;
    private final Decision decision;
    private final List<String> reasons;
    private final boolean fallbackUsed;

    public ScoreResult(double riskScore, Decision decision, List<String> reasons, boolean fallbackUsed) {
        this.riskScore = riskScore;
        this.decision = decision;
        this.reasons = reasons != null ? reasons : Collections.emptyList();
        this.fallbackUsed = fallbackUsed;
    }

    public double getRiskScore() {
        return riskScore;
    }

    public Decision getDecision() {
        return decision;
    }

    public List<String> getReasons() {
        return reasons;
    }

    public boolean isFallbackUsed() {
        return fallbackUsed;
    }

    @Override
    public String toString() {
        return "ScoreResult{" +
                "riskScore=" + riskScore +
                ", decision=" + decision +
                ", reasons=" + reasons +
                ", fallbackUsed=" + fallbackUsed +
                '}';
    }
}
