package com.fraudshield;

import com.fraudshield.fraud.RuleBasedScorer;
import com.fraudshield.model.Decision;
import com.fraudshield.model.Order;
import com.fraudshield.model.OrderItem;
import com.fraudshield.model.ScoreResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class RuleAndVelocityFallbackTest {

    @Test
    @DisplayName("Fallback velocity + geo rules trigger deterministic risk escalation")
    public void testVelocityAndGeoRules() {
        RuleBasedScorer scorer = new RuleBasedScorer();

        // Low amount but 5 rapid transactions + Arctic geo-spoof -> must BLOCK
        Order velocityGeoAttack = new Order(
                "ORD_VEL_GEO", "MERCHANT_1", "CUST_BOT",
                List.of(new OrderItem("PROD_SHIRT", 1, 1899.0)), 1899.0,
                "10.0.0.1", "FP_BOT", 78.21, 162.4, 5, true
        );

        ScoreResult result = scorer.score(velocityGeoAttack);
        assertTrue(result.isFallbackUsed());
        assertEquals(Decision.BLOCK, result.getDecision(), "Velocity + Geo spoof must BLOCK even for ₹1,899 order");
        assertTrue(result.getRiskScore() >= 0.70);
        assertEquals("JAVA_RULE_FALLBACK", result.getEngineBreakdown().get("engine"));
    }

    @Test
    @DisplayName("Legitimate baseline order receives APPROVE with low risk score")
    public void testLegitimateBaselineOrder() {
        RuleBasedScorer scorer = new RuleBasedScorer();
        Order safeOrder = new Order(
                "ORD_SAFE", "MERCHANT_1", "CUST_SAFE",
                List.of(new OrderItem("PROD_SHIRT", 1, 1899.0)), 1899.0,
                "192.168.1.5", "FP_OK", 19.0760, 72.8777, 1, true
        );

        ScoreResult result = scorer.score(safeOrder);
        assertEquals(Decision.APPROVE, result.getDecision());
        assertTrue(result.getRiskScore() < 0.35);
    }
}
