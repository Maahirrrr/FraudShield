package com.fraudshield.fraud;

import com.fraudshield.model.Decision;
import com.fraudshield.model.Order;
import com.fraudshield.model.ScoreResult;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * Local deterministic fallback scorer derived directly from Exploratory Data Analysis (EDA) findings.
 * Evaluates amount anomalies, night-time ordering, and geographic discrepancies without needing external AI services.
 */
public class RuleBasedScorer implements FraudScorer {

    @Override
    public ScoreResult score(Order order) {
        double score = 0.05; // Baseline low risk
        List<String> reasons = new ArrayList<>();

        // 1. Transaction Amount Anomaly (EDA: Fraud amounts have significant right skew)
        if (order.getTotalAmount() > 8000.0) {
            score += 0.70;
            reasons.add("Critical transaction amount anomaly (₹" + order.getTotalAmount() + " > ₹8,000 threshold)");
        } else if (order.getTotalAmount() > 3000.0) {
            score += 0.35;
            reasons.add("Above-average cart value (₹" + order.getTotalAmount() + ")");
        }

        // 2. High-Risk Night Hours (EDA: elevated fraud between 11 PM and 5 AM)
        LocalDateTime ldt = LocalDateTime.ofInstant(order.getCreatedAt(), ZoneId.systemDefault());
        int hour = ldt.getHour();
        if (hour >= 23 || hour <= 4) {
            score += 0.25;
            reasons.add("Unusual transaction hour (" + hour + ":00 IST - elevated fraud window)");
        }

        // 3. Location coordinates anomaly (Distance from merchant/home)
        if (Math.abs(order.getShippingLatitude()) > 70.0 || Math.abs(order.getShippingLongitude()) > 150.0) {
            score += 0.35;
            reasons.add("Geographic anomaly / synthetic location coordinates detected");
        }

        // Cap score at 1.0
        score = Math.min(1.0, score);

        // Map to Decision
        Decision decision;
        if (score >= 0.70) {
            decision = Decision.BLOCK;
        } else if (score >= 0.35) {
            decision = Decision.VERIFY;
        } else {
            decision = Decision.APPROVE;
            if (reasons.isEmpty()) {
                reasons.add("Normal transaction metrics within legitimate baseline");
            }
        }

        return new ScoreResult(score, decision, reasons, true);
    }
}
