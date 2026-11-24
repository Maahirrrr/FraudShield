package com.fraudshield.exception;

public class PaymentFailedException extends Exception {
    private final String reasonCode;

    public PaymentFailedException(String message, String reasonCode) {
        super(message);
        this.reasonCode = reasonCode;
    }

    public String getReasonCode() { return reasonCode; }
}
