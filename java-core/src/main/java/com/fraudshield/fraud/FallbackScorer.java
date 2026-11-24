package com.fraudshield.fraud;

import com.fraudshield.exception.ModelServiceUnavailableException;
import com.fraudshield.exception.ScoringException;
import com.fraudshield.model.Order;
import com.fraudshield.model.ScoreResult;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Resilient Scorer implementing the Fallback Pattern.
 * Calls RemoteModelScorer first; upon timeout or network failure,
 * immediately routes to RuleBasedScorer with zero store downtime.
 */
public class FallbackScorer implements FraudScorer {
    private static final Logger LOGGER = Logger.getLogger(FallbackScorer.class.getName());

    private final FraudScorer remoteScorer;
    private final FraudScorer ruleBasedScorer;
    private volatile boolean lastCallUsedFallback = false;

    public FallbackScorer(FraudScorer remoteScorer, FraudScorer ruleBasedScorer) {
        this.remoteScorer = remoteScorer;
        this.ruleBasedScorer = ruleBasedScorer;
    }

    @Override
    public ScoreResult score(Order order) throws ScoringException {
        try {
            ScoreResult result = remoteScorer.score(order);
            lastCallUsedFallback = false;
            return result;
        } catch (ModelServiceUnavailableException e) {
            LOGGER.log(Level.WARNING, "[RESILIENCE] AI Model Service unavailable (Timeout / Down). Activating local RuleBasedScorer fallback: " + e.getMessage());
            lastCallUsedFallback = true;
            ScoreResult fallbackResult = ruleBasedScorer.score(order);
            return new ScoreResult(
                    fallbackResult.getRiskScore(),
                    fallbackResult.getDecision(),
                    fallbackResult.getReasons(),
                    true // fallbackUsed = true
            );
        }
    }

    public boolean isLastCallUsedFallback() {
        return lastCallUsedFallback;
    }
}
