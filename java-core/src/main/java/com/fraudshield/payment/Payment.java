package com.fraudshield.payment;

import com.fraudshield.exception.PaymentFailedException;
import com.fraudshield.model.Order;

/**
 * Abstract Payment base demonstrating inheritance and polymorphism.
 */
public abstract class Payment {
    private final String paymentId;
    private final String method;

    public Payment(String paymentId, String method) {
        this.paymentId = paymentId;
        this.method = method;
    }

    public String getPaymentId() { return paymentId; }
    public String getMethod() { return method; }

    /**
     * Polymorphic payment execution across Card, UPI, and COD.
     */
    public abstract boolean process(Order order) throws PaymentFailedException;
}
