package com.fraudshield;

import com.fraudshield.exception.ModelServiceUnavailableException;
import com.fraudshield.exception.PaymentFailedException;
import com.fraudshield.fraud.FallbackScorer;
import com.fraudshield.fraud.RuleBasedScorer;
import com.fraudshield.model.Order;
import com.fraudshield.model.OrderItem;
import com.fraudshield.model.ScoreResult;
import com.fraudshield.payment.CardPayment;
import com.fraudshield.payment.CodPayment;
import com.fraudshield.payment.UpiPayment;
import com.fraudshield.service.InventoryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class PaymentExceptionTest {

    @Test
    @DisplayName("Custom Exception Test: PaymentFailedException for Card, UPI, and COD limits")
    public void testPaymentFailurePaths() {
        Order normalOrder = new Order(
                "ORD_PAY_1", "MERCHANT_1", "CUST_1",
                List.of(new OrderItem("PROD_SHIRT", 1, 1899.0)),
                1899.0, "127.0.0.1", "fp_1", 19.07, 72.87
        );
        Order bigCodOrder = new Order(
                "ORD_PAY_2", "MERCHANT_1", "CUST_1",
                List.of(new OrderItem("PROD_WATCH", 2, 9500.0)),
                19000.0, "127.0.0.1", "fp_1", 19.07, 72.87
        );

        PaymentFailedException cardEx = assertThrows(PaymentFailedException.class,
                () -> new CardPayment("PAY_C", "tok_visa_0000", "0000").process(normalOrder));
        assertEquals("ERR_CARD_DECLINED", cardEx.getReasonCode());

        PaymentFailedException upiEx = assertThrows(PaymentFailedException.class,
                () -> new UpiPayment("PAY_U", "fail@okaxis").process(normalOrder));
        assertEquals("ERR_UPI_REJECTED", upiEx.getReasonCode());

        PaymentFailedException codEx = assertThrows(PaymentFailedException.class,
                () -> new CodPayment("PAY_COD", "+919999999999").process(bigCodOrder));
        assertEquals("ERR_COD_LIMIT_EXCEEDED", codEx.getReasonCode());
    }

    @Test
    @DisplayName("Resilience & Concurrency Demo Test: forceFallback + side-by-side 50-thread race comparison")
    @SuppressWarnings("unchecked")
    public void testSimulatedFallbackAndRaceComparison() throws Exception {
        FallbackScorer scorer = new FallbackScorer(
                order -> { throw new AssertionError("Should skip remote when forceFallback=true"); },
                new RuleBasedScorer()
        );
        Order forcedOrder = new Order(
                "ORD_FB_1", "MERCHANT_1", "CUST_1",
                List.of(new OrderItem("PROD_SHIRT", 1, 1899.0)),
                1899.0, "127.0.0.1", "fp_1", 19.07, 72.87, 1, true, true
        );
        ScoreResult res = scorer.score(forcedOrder);
        assertTrue(res.isFallbackUsed(), "Forced fallback must execute RuleBasedScorer cleanly");
        assertTrue(scorer.isLastCallUsedFallback());

        InventoryService svc = new InventoryService();
        Map<String, Object> cmp = svc.runRaceConditionComparison(50, 10);
        Map<String, Object> synced = (Map<String, Object>) cmp.get("synchronized");
        assertEquals(10, synced.get("succeeded"));
        assertEquals(40, synced.get("rejected_out_of_stock"));
        assertEquals(0, synced.get("final_stock"));
        assertEquals(0, synced.get("oversold_units"));
    }
}
