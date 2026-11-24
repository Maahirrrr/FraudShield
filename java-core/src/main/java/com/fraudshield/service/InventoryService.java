package com.fraudshield.service;

import com.fraudshield.exception.OutOfStockException;
import com.fraudshield.model.Product;

import java.util.concurrent.ConcurrentHashMap;

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

    /**
     * VIVA RACE CONDITION PROOF: Unsynchronized version susceptible to overselling under load.
     */
    public boolean decrementStockUnsynchronized(String productId, int quantity) throws OutOfStockException {
        Product p = products.get(productId);
        if (p == null) return false;

        if (p.getStock() >= quantity) {
            // Artificial slight delay to exaggerate thread context switch during race condition
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
}
