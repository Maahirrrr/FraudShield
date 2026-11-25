package com.fraudshield.model;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Encapsulates the fraud score, final decision, plain-language reasons,
 * whether fallback was used, latency, and multi-engine telemetry breakdown.
 */
public class ScoreResult {
    private final double riskScore;
    private final Decision decision;
    private final List<String> reasons;
    private final boolean fallbackUsed;
    private final double latencyMs;
    private final Map<String, Object> engineBreakdown;

    public ScoreResult(double riskScore, Decision decision, List<String> reasons, boolean fallbackUsed) {
        this(riskScore, decision, reasons, fallbackUsed, 0.0, Collections.emptyMap());
    }

    public ScoreResult(double riskScore, Decision decision, List<String> reasons, boolean fallbackUsed,
                       double latencyMs, Map<String, Object> engineBreakdown) {
        this.riskScore = riskScore;
        this.decision = decision;
        this.reasons = reasons != null ? reasons : Collections.emptyList();
        this.fallbackUsed = fallbackUsed;
        this.latencyMs = latencyMs;
        this.engineBreakdown = engineBreakdown != null ? engineBreakdown : Collections.emptyMap();
    }

    public double getRiskScore() { return riskScore; }
    public Decision getDecision() { return decision; }
    public List<String> getReasons() { return reasons; }
    public boolean isFallbackUsed() { return fallbackUsed; }
    public double getLatencyMs() { return latencyMs; }
    public Map<String, Object> getEngineBreakdown() { return engineBreakdown; }

    @Override
    public String toString() {
        return "ScoreResult{" +
                "riskScore=" + riskScore +
                ", decision=" + decision +
                ", reasons=" + reasons +
                ", fallbackUsed=" + fallbackUsed +
                ", latencyMs=" + latencyMs +
                '}';
    }
}
