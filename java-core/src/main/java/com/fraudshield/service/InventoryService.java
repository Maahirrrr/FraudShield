package com.fraudshield.service;

import com.fraudshield.exception.OutOfStockException;
import com.fraudshield.model.Product;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
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

    public Collection<Product> getAllProducts() {
        List<Product> list = new ArrayList<>(products.values());
        list.sort(Comparator.comparingDouble(Product::getPrice));
        return list;
    }

    public synchronized void resetDemoCatalog() {
        setStockOrAdd("PROD_SHIRT", "Heritage Linen Shirt", 1899.0, 50);
        setStockOrAdd("PROD_SHOES", "Trail Runner Pro", 4499.0, 30);
        setStockOrAdd("PROD_WATCH", "Chronograph Minimalist", 9500.0, 10);
    }

    private void setStockOrAdd(String id, String name, double price, int stock) {
        Product existing = products.get(id);
        if (existing != null) {
            existing.setStock(stock);
        } else {
            products.put(id, new Product(id, name, price, stock));
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
}
