package com.fraudshield.service;

import com.fraudshield.exception.OutOfStockException;
import com.fraudshield.model.Product;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Thread-safe inventory service with explicit synchronized vs unsynchronized methods
 * to demonstrate race conditions and atomic stock controls during examination.
 */
public class InventoryService {
    private final ConcurrentHashMap<String, Product> products = new ConcurrentHashMap<>();

    public void addProduct(Product product) {
        products.put(product.getId(), product);
    }

    public Product getProduct(String productId) {
        return products.get(productId);
    }

    public Collection<Product> getAllProducts() {
        List<Product> list = new ArrayList<>(products.values());
        list.sort(Comparator.comparingDouble(Product::getPrice));
        return list;
    }

    public synchronized void resetDemoCatalog() {
        setStockOrAdd("PROD_CAP", "Breathable Running Cap", "Apparel", 649.0, 75);
        setStockOrAdd("PROD_BOTTLE", "Vacuum Insulated Flask", "Home", 799.0, 60);
        setStockOrAdd("PROD_TEE", "Organic Cotton Heavy Tee", "Apparel", 899.0, 80);
        setStockOrAdd("PROD_TOTE", "Waxed Canvas Tote", "Accessories", 1299.0, 45);
        setStockOrAdd("PROD_BELT", "Full-Grain Leather Belt", "Accessories", 1499.0, 40);
        setStockOrAdd("PROD_SHIRT", "Heritage Linen Shirt", "Apparel", 1899.0, 50);
        setStockOrAdd("PROD_BACKPACK", "Commuter Laptop Pack", "Accessories", 3899.0, 25);
        setStockOrAdd("PROD_SHOES", "Trail Runner Pro", "Footwear", 4499.0, 30);
        setStockOrAdd("PROD_EARBUDS", "ANC Studio Earbuds", "Audio", 5299.0, 22);
        setStockOrAdd("PROD_JACKET", "Weatherproof Field Jacket", "Apparel", 6499.0, 18);
        setStockOrAdd("PROD_WATCH", "Chronograph Minimalist", "Watches", 9500.0, 10);
        setStockOrAdd("PROD_HEADPHONES", "Reference Wireless Headphones", "Audio", 11999.0, 10);
    }

    private void setStockOrAdd(String id, String name, String category, double price, int stock) {
        Product existing = products.get(id);
        if (existing != null) {
            existing.setStock(stock);
        } else {
            products.put(id, new Product(id, name, category, price, stock));
        }
    }

    /**
     * RACE CONDITION DEMO: Unsynchronized check-then-act susceptible to overselling.
     */
    public boolean decrementStockUnsynchronized(String productId, int quantity) throws OutOfStockException {
        Product p = products.get(productId);
        if (p == null) return false;

        if (p.getStock() >= quantity) {
            try { Thread.sleep(2); } catch (InterruptedException ignored) {}
            p.setStock(p.getStock() - quantity);
            return true;
        } else {
            throw new OutOfStockException(productId, quantity, p.getStock());
        }
    }

    /**
     * PRODUCTION SYNCHRONIZED VERSION: Thread-safe atomic stock decrement.
     */
    public synchronized boolean decrementStockSynchronized(String productId, int quantity) throws OutOfStockException {
        Product p = products.get(productId);
        if (p == null) return false;

        if (p.getStock() >= quantity) {
            p.setStock(p.getStock() - quantity);
            return true;
        } else {
            throw new OutOfStockException(productId, quantity, p.getStock());
        }
    }

    /**
     * Executes a live side-by-side concurrency experiment comparing unsynchronized vs synchronized stock locks.
     */
    public Map<String, Object> runRaceConditionComparison(int threads, int initialStock) throws InterruptedException {
        int safeThreads = Math.max(2, Math.min(200, threads));
        int safeStock = Math.max(1, Math.min(100, initialStock));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("threads", safeThreads);
        result.put("initial_stock", safeStock);
        result.put("unsynchronized", executeRaceBatch(false, safeThreads, safeStock));
        result.put("synchronized", executeRaceBatch(true, safeThreads, safeStock));
        return result;
    }

    private Map<String, Object> executeRaceBatch(boolean useSynchronized, int threads, int initialStock) throws InterruptedException {
        InventoryService isolated = new InventoryService();
        String sku = "PROD_RACE_BENCH";
        isolated.addProduct(new Product(sku, "Concurrency Benchmark SKU", "Demo", 999.0, initialStock));

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch readyGate = new CountDownLatch(threads);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch doneGate = new CountDownLatch(threads);

        AtomicInteger succeeded = new AtomicInteger(0);
        AtomicInteger rejected = new AtomicInteger(0);

        long startNs = System.nanoTime();
        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                readyGate.countDown();
                try {
                    startGate.await();
                    if (useSynchronized) {
                        if (isolated.decrementStockSynchronized(sku, 1)) {
                            succeeded.incrementAndGet();
                        }
                    } else {
                        if (isolated.decrementStockUnsynchronized(sku, 1)) {
                            succeeded.incrementAndGet();
                        }
                    }
                } catch (OutOfStockException e) {
                    rejected.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneGate.countDown();
                }
            });
        }

        readyGate.await();
        startGate.countDown();
        doneGate.await();
        pool.shutdown();
        double elapsedMs = Math.round(((System.nanoTime() - startNs) / 1_000_000.0) * 100.0) / 100.0;

        int finalStock = isolated.getProduct(sku).getStock();
        int oversold = Math.max(0, succeeded.get() - initialStock);

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("succeeded", succeeded.get());
        stats.put("rejected_out_of_stock", rejected.get());
        stats.put("final_stock", finalStock);
        stats.put("oversold_units", oversold);
        stats.put("elapsed_ms", elapsedMs);
        return stats;
    }
}
