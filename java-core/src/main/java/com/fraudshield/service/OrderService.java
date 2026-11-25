package com.fraudshield.service;

import com.fraudshield.exception.*;
import com.fraudshield.fraud.FraudScorer;
import com.fraudshield.model.*;
import com.fraudshield.payment.Payment;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Logger;

/**
 * Multithreaded order orchestrator.
 * Tracks per-customer 1h velocity, enforces DPDP consent & atomic inventory,
 * calls resilient ML/DL scorer, and dispatches async CSV audit writes via BlockingQueue.
 */
public class OrderService {
    private static final Logger LOGGER = Logger.getLogger(OrderService.class.getName());

    private final BlockingQueue<Transaction> auditQueue = new LinkedBlockingQueue<>(1000);
    private final ExecutorService workerPool = Executors.newFixedThreadPool(2);
    private final ScheduledExecutorService backgroundScheduler = Executors.newScheduledThreadPool(1);

    // Tracks recent 1h timestamps per (customerId + deviceFingerprint) for real velocity enforcement
    private final ConcurrentHashMap<String, Deque<Instant>> velocityMap = new ConcurrentHashMap<>();
    // Tracks recent end-to-end scoring latencies (ms) for real p95 metrics
    private final ConcurrentLinkedDeque<Double> latencySamples = new ConcurrentLinkedDeque<>();

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
        for (int i = 0; i < 2; i++) {
            workerPool.submit(() -> {
                while (running || !auditQueue.isEmpty()) {
                    try {
                        Transaction tx = auditQueue.poll(300, TimeUnit.MILLISECONDS);
                        if (tx != null) {
                            fileStore.appendCsvExport(tx);
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
        backgroundScheduler.scheduleAtFixedRate(() -> {
            Instant cutoff = Instant.now().minus(1, ChronoUnit.HOURS);
            velocityMap.values().forEach(deque -> {
                synchronized (deque) {
                    while (!deque.isEmpty() && deque.peekFirst().isBefore(cutoff)) {
                        deque.pollFirst();
                    }
                }
            });
            LOGGER.fine("[SCHEDULER] Pruned stale 1h velocity windows.");
        }, 60, 60, TimeUnit.SECONDS);
    }

    private int recordVelocityAndCount(String customerId, String deviceFingerprint, int callerHint) {
        if (customerId == null || customerId.startsWith("CUST_STORE_GUEST")) {
            return Math.max(1, callerHint);
        }
        String key = customerId + ":" + (deviceFingerprint != null ? deviceFingerprint : "fp");
        Deque<Instant> deque = velocityMap.computeIfAbsent(key, k -> new ArrayDeque<>());
        Instant now = Instant.now();
        Instant cutoff = now.minus(1, ChronoUnit.HOURS);
        int tracked;
        synchronized (deque) {
            while (!deque.isEmpty() && deque.peekFirst().isBefore(cutoff)) {
                deque.pollFirst();
            }
            deque.addLast(now);
            tracked = deque.size();
        }
        return Math.max(callerHint, tracked);
    }

    private void recordLatency(double ms) {
        latencySamples.addLast(ms);
        while (latencySamples.size() > 200) {
            latencySamples.pollFirst();
        }
    }

    public double getP95LatencyMs() {
        List<Double> snapshot = new ArrayList<>(latencySamples);
        if (snapshot.isEmpty()) return 0.0;
        Collections.sort(snapshot);
        int idx = Math.min(snapshot.size() - 1, (int) Math.ceil(0.95 * snapshot.size()) - 1);
        return Math.round(snapshot.get(idx) * 100.0) / 100.0;
    }

    /**
     * Synchronously processes an incoming order end-to-end.
     */
    public Transaction processOrder(Order order, Payment payment)
            throws InvalidInputException, OutOfStockException, FraudBlockedException, PaymentFailedException, ScoringException {

        long startNs = System.nanoTime();

        // 1. Validation & DPDP Act 2023 Consent Enforcement
        if (order == null || order.getItems() == null || order.getItems().isEmpty()) {
            throw new InvalidInputException("Order must contain at least one valid product item.");
        }
        if (order.getTotalAmount() <= 0) {
            throw new InvalidInputException("Order total amount must be strictly greater than 0.");
        }
        if (!order.isConsentGiven()) {
            throw new InvalidInputException("DPDP Act 2023 explicit consent is required before transaction telemetry scoring.");
        }

        // 2. Thread-safe Stock Deduction
        for (OrderItem item : order.getItems()) {
            inventoryService.decrementStockSynchronized(item.getProductId(), item.getQuantity());
        }

        // 3. Velocity Tracking & Multi-Engine Fraud Scoring with Automatic Fallback
        int effectiveVelocity = recordVelocityAndCount(
                order.getCustomerId(), order.getDeviceFingerprint(), order.getRecentTxCount1h());
        Order enrichedOrder = (effectiveVelocity == order.getRecentTxCount1h())
                ? order
                : new Order(order.getId(), order.getMerchantId(), order.getCustomerId(),
                        order.getItems(), order.getTotalAmount(), order.getIpAddress(),
                        order.getDeviceFingerprint(), order.getShippingLatitude(),
                        order.getShippingLongitude(), effectiveVelocity, order.isConsentGiven());

        ScoreResult scoreResult = fraudScorer.score(enrichedOrder);

        double totalMs = Math.round(((System.nanoTime() - startNs) / 1_000_000.0) * 100.0) / 100.0;
        double reportedLatency = scoreResult.getLatencyMs() > 0 ? Math.max(totalMs, scoreResult.getLatencyMs()) : totalMs;
        recordLatency(reportedLatency);

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
                scoreResult.isFallbackUsed(),
                reportedLatency,
                scoreResult.getEngineBreakdown()
        );

        // 4. Persist JSONL immediately & queue async CSV export
        fileStore.saveTransactionJsonl(transaction);
        if (!auditQueue.offer(transaction)) {
            fileStore.appendCsvExport(transaction);
        }

        // 5. Enforce Fraud Decision
        if (scoreResult.getDecision() == Decision.BLOCK) {
            order.setStatus("BLOCKED_FRAUD");
            throw new FraudBlockedException(
                    "Order blocked by FraudShield risk policy. Score: " + scoreResult.getRiskScore(),
                    transaction.getId(),
                    order.getId(),
                    scoreResult.getRiskScore(),
                    scoreResult.getReasons(),
                    scoreResult.isFallbackUsed(),
                    reportedLatency,
                    scoreResult.getEngineBreakdown()
            );
        }

        // 6. Execute Payment Polymorphically (Card / UPI / COD)
        boolean paid = paymentService.executePayment(order, payment);
        if (paid) {
            order.setStatus(scoreResult.getDecision() == Decision.VERIFY ? "VERIFY_REQUIRED" : "CONFIRMED");
        }

        return transaction;
    }

    public void shutdown() {
        running = false;
        workerPool.shutdown();
        backgroundScheduler.shutdown();
    }
}
