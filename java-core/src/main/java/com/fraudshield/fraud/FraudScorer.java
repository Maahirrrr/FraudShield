package com.fraudshield.fraud;

import com.fraudshield.exception.ScoringException;
import com.fraudshield.model.Order;
import com.fraudshield.model.ScoreResult;

/**
 * Interface defining fraud evaluation strategy.
 */
public interface FraudScorer {
    ScoreResult score(Order order) throws ScoringException;
}
