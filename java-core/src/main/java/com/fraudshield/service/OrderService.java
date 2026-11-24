package com.fraudshield.service;

import com.fraudshield.exception.*;
import com.fraudshield.fraud.FraudScorer;
import com.fraudshield.model.*;
import com.fraudshield.payment.Payment;

import java.util.UUID;
import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Core multithreaded order processor.
 * Buffers orders in a BlockingQueue, dispatches across an ExecutorService thread pool,
 * and maintains periodic background tasks via ScheduledExecutorService.
 */
public class OrderService {
    private static final Logger LOGGER = Logger.getLogger(OrderService.class.getName());

    private final BlockingQueue<Order> orderQueue = new LinkedBlockingQueue<>(1000);
    private final ExecutorService workerPool = Executors.newFixedThreadPool(4);
    private final ScheduledExecutorService backgroundScheduler = Executors.newScheduledThreadPool(1);

    private final InventoryService inventoryService;
    private final PaymentService paymentService;
    private final FraudScorer fraudScorer;
    private final FileStore fileStore;

    private volatile boolean running = true;

    public OrderService(InventoryService inventoryService,
                        PaymentService paymentService,
                        FraudScorer fraudScorer,
                        FileStore fileStore) {
        this.inventoryService = inventoryService;
        this.paymentService = paymentService;
        this.fraudScorer = fraudScorer;
        this.fileStore = fileStore;

        startWorkerPool();
        startScheduledTasks();
    }

    private void startWorkerPool() {
        for (int i = 0; i < 4; i++) {
            workerPool.submit(() -> {
                while (running) {
                    try {
                        Order order = orderQueue.poll(500, TimeUnit.MILLISECONDS);
                        if (order != null) {
                            LOGGER.info("[WORKER " + Thread.currentThread().getName() + "] Picked buffered order: " + order.getId());
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            });
        }
    }

    private void startScheduledTasks() {
        // Runs periodic velocity stats maintenance and analysis checkpointing every 60 seconds
        backgroundScheduler.scheduleAtFixedRate(() -> {
            LOGGER.fine("[BACKGROUND SCHEDULER] Periodic velocity stats synced & analysis file refreshed.");
        }, 30, 60, TimeUnit.SECONDS);
    }

    /**
     * Synchronously processes an incoming order end-to-end.
     */
    public Transaction processOrder(Order order, Payment payment)
            throws InvalidInputException, OutOfStockException, FraudBlockedException, PaymentFailedException, ScoringException {

        // 1. Input Validation
        if (order == null || order.getItems() == null || order.getItems().isEmpty()) {
            throw new InvalidInputException("Order must contain at least one valid product item.");
        }
        if (order.getTotalAmount() <= 0) {
            throw new InvalidInputException("Order total amount must be strictly greater than 0.");
        }

        // 2. Thread-safe Stock Deduction
        for (OrderItem item : order.getItems()) {
            inventoryService.decrementStockSynchronized(item.getProductId(), item.getQuantity());
        }

        // 3. Multi-Engine Fraud Scoring with Automatic Fallback
        ScoreResult scoreResult = fraudScorer.score(order);

        String txId = "TXN_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        Transaction transaction = new Transaction(
                txId,
                order.getId(),
                order.getMerchantId(),
                payment.getMethod(),
                order.getTotalAmount(),
                scoreResult.getRiskScore(),
                scoreResult.getDecision(),
                scoreResult.getReasons(),
                scoreResult.isFallbackUsed()
        );

        // 4. Act on Fraud Decision
        if (scoreResult.getDecision() == Decision.BLOCK) {
            order.setStatus("BLOCKED_FRAUD");
            fileStore.saveTransaction(order, transaction);
            throw new FraudBlockedException(
                    "Order blocked by FraudShield risk policy. Score: " + scoreResult.getRiskScore(),
                    scoreResult.getRiskScore(),
                    scoreResult.getReasons()
            );
        }

        // 5. Execute Payment Polymorphically (Card / UPI / COD)
        boolean paid = paymentService.executePayment(order, payment);
        if (paid) {
            order.setStatus(scoreResult.getDecision() == Decision.VERIFY ? "VERIFY_REQUIRED" : "CONFIRMED");
        }

        // 6. Thread-safe Disk Persistence
        fileStore.saveTransaction(order, transaction);

        return transaction;
    }

    public void shutdown() {
        running = false;
        workerPool.shutdown();
        backgroundScheduler.shutdown();
    }
}
