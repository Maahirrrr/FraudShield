package com.fraudshield.fraud;

import com.fraudshield.model.Decision;
import com.fraudshield.model.Order;
import com.fraudshield.model.ScoreResult;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Local deterministic fallback scorer derived from Exploratory Data Analysis (EDA) findings.
 * Evaluates amount anomalies, night-time ordering, velocity bursts, and geographic discrepancies.
 */
public class RuleBasedScorer implements FraudScorer {

    @Override
    public ScoreResult score(Order order) {
        long startNs = System.nanoTime();
        double score = 0.05; // Baseline low risk
        List<String> reasons = new ArrayList<>();

        // 1. Transaction Amount Anomaly (EDA: high-value outliers trigger secondary scrutiny)
        if (order.getTotalAmount() > 8000.0) {
            score += 0.70;
            reasons.add(String.format("High amount outlier (₹%,.2f > ₹8,000 threshold)", order.getTotalAmount()));
        } else if (order.getTotalAmount() > 3000.0) {
            score += 0.35;
            reasons.add(String.format("Above-average order value (₹%,.2f)", order.getTotalAmount()));
        }

        // 2. Temporal Window Risk (late night / early morning elevated risk)
        LocalDateTime ldt = LocalDateTime.ofInstant(order.getCreatedAt(), ZoneId.systemDefault());
        int hour = ldt.getHour();
        if (hour >= 23 || hour <= 4) {
            score += 0.25;
            reasons.add("Elevated risk hour window (" + hour + ":00 local time)");
        }

        // 3. Velocity Spike / Card-Testing Burst
        if (order.getRecentTxCount1h() >= 4) {
            score += 0.45;
            reasons.add("Velocity burst (" + order.getRecentTxCount1h() + " orders within 60 minutes)");
        }

        // 4. Geographic Coordinate Discrepancy
        if (Math.abs(order.getShippingLatitude()) > 60.0 || Math.abs(order.getShippingLongitude()) > 130.0) {
            score += 0.38;
            reasons.add(String.format("Anomalous coordinate displacement (%.2f, %.2f)",
                    order.getShippingLatitude(), order.getShippingLongitude()));
        }

        score = Math.min(1.0, Math.round(score * 10000.0) / 10000.0);

        Decision decision;
        if (score >= 0.70) {
            decision = Decision.BLOCK;
        } else if (score >= 0.35) {
            decision = Decision.VERIFY;
        } else {
            decision = Decision.APPROVE;
            if (reasons.isEmpty()) {
                reasons.add("Deterministic baseline check passed (low amount and velocity)");
            }
        }

        double latencyMs = Math.round(((System.nanoTime() - startNs) / 1_000_000.0) * 100.0) / 100.0;
        Map<String, Object> breakdown = new LinkedHashMap<>();
        breakdown.put("engine", "JAVA_RULE_FALLBACK");
        breakdown.put("recent_tx_count_1h", order.getRecentTxCount1h());
        breakdown.put("rules_triggered", reasons.size());

        return new ScoreResult(score, decision, reasons, true, latencyMs, breakdown);
    }
}
