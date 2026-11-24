package com.fraudshield;

import com.fraudshield.exception.OutOfStockException;
import com.fraudshield.model.Product;
import com.fraudshield.service.InventoryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class InventoryConcurrencyTest {

    @Test
    @DisplayName("Multithreading Test: 50 concurrent threads purchasing 10 available units")
    public void testConcurrentStockDeductionNoOverselling() throws InterruptedException {
        InventoryService inventory = new InventoryService();
        String productId = "PROD_LIMITED_EDITION";
        int initialStock = 10;
        inventory.addProduct(new Product(productId, "Limited Edition Sneaker", 9999.0, initialStock));

        int numberOfThreads = 50;
        ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch endGate = new CountDownLatch(numberOfThreads);

        AtomicInteger successfulOrders = new AtomicInteger(0);
        AtomicInteger outOfStockRejections = new AtomicInteger(0);

        for (int i = 0; i < numberOfThreads; i++) {
            executor.submit(() -> {
                try {
                    startGate.await(); // Ensure all threads strike simultaneously
                    boolean success = inventory.decrementStockSynchronized(productId, 1);
                    if (success) {
                        successfulOrders.incrementAndGet();
                    }
                } catch (OutOfStockException e) {
                    outOfStockRejections.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    endGate.countDown();
                }
            });
        }

        // Fire all threads simultaneously
        startGate.countDown();
        endGate.await();
        executor.shutdown();

        // Verification
        assertEquals(10, successfulOrders.get(), "Exactly 10 orders must succeed");
        assertEquals(40, outOfStockRejections.get(), "Exactly 40 orders must be rejected with OutOfStockException");
        assertEquals(0, inventory.getProduct(productId).getStock(), "Remaining inventory must be exactly 0 (no overselling)");
    }
}
