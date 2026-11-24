package com.fraudshield.exception;

public class OutOfStockException extends Exception {
    private final String productId;
    private final int requested;
    private final int available;

    public OutOfStockException(String productId, int requested, int available) {
        super(String.format("Product '%s' is out of stock. Requested: %d, Available: %d", productId, requested, available));
        this.productId = productId;
        this.requested = requested;
        this.available = available;
    }

    public String getProductId() { return productId; }
    public int getRequested() { return requested; }
    public int getAvailable() { return available; }
}
