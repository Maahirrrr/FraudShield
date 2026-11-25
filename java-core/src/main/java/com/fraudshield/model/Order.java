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
    private final int recentTxCount1h;
    private final boolean consentGiven;
    private final boolean forceFallback;
    private final Instant createdAt;
    private String status;

    public Order(String id, String merchantId, String customerId, List<OrderItem> items,
                 double totalAmount, String ipAddress, String deviceFingerprint,
                 double shippingLatitude, double shippingLongitude) {
        this(id, merchantId, customerId, items, totalAmount, ipAddress, deviceFingerprint,
                shippingLatitude, shippingLongitude, 1, true, false);
    }

    public Order(String id, String merchantId, String customerId, List<OrderItem> items,
                 double totalAmount, String ipAddress, String deviceFingerprint,
                 double shippingLatitude, double shippingLongitude,
                 int recentTxCount1h, boolean consentGiven) {
        this(id, merchantId, customerId, items, totalAmount, ipAddress, deviceFingerprint,
                shippingLatitude, shippingLongitude, recentTxCount1h, consentGiven, false);
    }

    public Order(String id, String merchantId, String customerId, List<OrderItem> items,
                 double totalAmount, String ipAddress, String deviceFingerprint,
                 double shippingLatitude, double shippingLongitude,
                 int recentTxCount1h, boolean consentGiven, boolean forceFallback) {
        this.id = id;
        this.merchantId = merchantId;
        this.customerId = customerId;
        this.items = items;
        this.totalAmount = totalAmount;
        this.ipAddress = ipAddress;
        this.deviceFingerprint = deviceFingerprint;
        this.shippingLatitude = shippingLatitude;
        this.shippingLongitude = shippingLongitude;
        this.recentTxCount1h = Math.max(1, recentTxCount1h);
        this.consentGiven = consentGiven;
        this.forceFallback = forceFallback;
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
    public int getRecentTxCount1h() { return recentTxCount1h; }
    public boolean isConsentGiven() { return consentGiven; }
    public boolean isForceFallback() { return forceFallback; }
    public Instant getCreatedAt() { return createdAt; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
