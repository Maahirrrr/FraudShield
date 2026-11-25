package com.fraudshield;

import com.fraudshield.model.Decision;
import com.fraudshield.model.Order;
import com.fraudshield.model.OrderItem;
import com.fraudshield.model.Transaction;
import com.fraudshield.service.FileStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class FileStoreErasureTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("DPDP Act 2023 Section 12: save, decision update, and full erasure by order_id")
    public void testPersistenceUpdateAndDpdpErasure() {
        Path logPath = tempDir.resolve("transactions.jsonl");
        Path csvPath = tempDir.resolve("analysis_export.csv");
        FileStore store = new FileStore(logPath.toString(), csvPath.toString());

        Order order = new Order(
                "ORD_DPDP_101", "MERCH_1", "CUST_1",
                List.of(new OrderItem("PROD_SHIRT", 1, 1899.0)), 1899.0,
                "127.0.0.1", "FP_1", 19.0, 72.8
        );
        Transaction tx = new Transaction(
                "TXN_DPDP_101", "ORD_DPDP_101", "MERCH_1", "UPI",
                1899.0, 0.45, Decision.VERIFY, List.of("Moderate risk"), false
        );

        store.saveTransaction(order, tx);
        assertEquals(1, store.loadAllTransactions().size());

        boolean updated = store.updateTransactionDecision("TXN_DPDP_101", "APPROVE", "OTP verified");
        assertTrue(updated);
        assertEquals("APPROVE", store.loadRecentTransactions(1).get(0).get("decision").getAsString());

        int removed = store.eraseOrderData("ORD_DPDP_101");
        assertEquals(1, removed);
        assertTrue(store.loadAllTransactions().isEmpty(), "Record must be purged from JSONL under DPDP Section 12");
    }
}
