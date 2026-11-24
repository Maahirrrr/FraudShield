package com.fraudshield;

import com.fraudshield.fraud.FallbackScorer;
import com.fraudshield.fraud.RemoteModelScorer;
import com.fraudshield.fraud.RuleBasedScorer;
import com.fraudshield.model.Decision;
import com.fraudshield.model.Order;
import com.fraudshield.model.OrderItem;
import com.fraudshield.model.ScoreResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class FallbackScorerTest {

    @Test
    @DisplayName("Resilience Test: Remote Scorer down -> FallbackScorer activates RuleBasedScorer")
    public void testGracefulFallbackWhenRemoteUnavailable() throws Exception {
        // Point remote scorer to an unreachable port to simulate server outage
        RemoteModelScorer unreachableRemoteScorer = new RemoteModelScorer("http://localhost:59999");
        RuleBasedScorer ruleBasedScorer = new RuleBasedScorer();
        FallbackScorer fallbackScorer = new FallbackScorer(unreachableRemoteScorer, ruleBasedScorer);

        Order testOrder = new Order(
                "ORD_TEST_99", "MERCHANT_TEST", "CUST_TEST",
                List.of(new OrderItem("PROD_1", 1, 9500.0)), 9500.0,
                "192.168.1.1", "FP_TEST", 19.0760, 72.8777
        );

        // Must not throw an exception; must gracefully return fallback result
        ScoreResult result = fallbackScorer.score(testOrder);

        assertNotNull(result);
        assertTrue(result.isFallbackUsed(), "Fallback flag must be true");
        assertTrue(fallbackScorer.isLastCallUsedFallback());
        assertEquals(Decision.BLOCK, result.getDecision(), "Order > ₹8000 should be BLOCKED by rule-based fallback");
        assertFalse(result.getReasons().isEmpty(), "Reasons list must explain the fallback decision");
    }
}
