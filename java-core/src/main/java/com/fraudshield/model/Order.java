package com.fraudshield.model;

import java.time.Instant;
import java.util.List;

public class Order {
    private final String id;
    private final String merchantId;
    private final String customerId;
    private final List<OrderItem> items;
    private final double totalAmount;
    private final String ipAddress;
    private final String deviceFingerprint;
    private final double shippingLatitude;
    private final double shippingLongitude;
    private final Instant createdAt;
    private String status;

    public Order(String id, String merchantId, String customerId, List<OrderItem> items,
                 double totalAmount, String ipAddress, String deviceFingerprint,
                 double shippingLatitude, double shippingLongitude) {
        this.id = id;
        this.merchantId = merchantId;
        this.customerId = customerId;
        this.items = items;
        this.totalAmount = totalAmount;
        this.ipAddress = ipAddress;
        this.deviceFingerprint = deviceFingerprint;
        this.shippingLatitude = shippingLatitude;
        this.shippingLongitude = shippingLongitude;
        this.createdAt = Instant.now();
        this.status = "PENDING";
    }

    public String getId() { return id; }
    public String getMerchantId() { return merchantId; }
    public String getCustomerId() { return customerId; }
    public List<OrderItem> getItems() { return items; }
    public double getTotalAmount() { return totalAmount; }
    public String getIpAddress() { return ipAddress; }
    public String getDeviceFingerprint() { return deviceFingerprint; }
    public double getShippingLatitude() { return shippingLatitude; }
    public double getShippingLongitude() { return shippingLongitude; }
    public Instant getCreatedAt() { return createdAt; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
