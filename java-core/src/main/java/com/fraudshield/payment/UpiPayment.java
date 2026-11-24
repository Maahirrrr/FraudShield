package com.fraudshield.payment;

import com.fraudshield.exception.PaymentFailedException;
import com.fraudshield.model.Order;

public class UpiPayment extends Payment {
    private final String vpa;

    public UpiPayment(String paymentId, String vpa) {
        super(paymentId, "UPI");
        this.vpa = vpa;
    }

    public String getVpa() { return vpa; }

    @Override
    public boolean process(Order order) throws PaymentFailedException {
        if (vpa == null || !vpa.contains("@")) {
            throw new PaymentFailedException("Invalid UPI ID / VPA format: " + vpa, "ERR_INVALID_VPA");
        }
        if (vpa.toLowerCase().startsWith("fail")) {
            throw new PaymentFailedException("UPI collect request rejected by customer.", "ERR_UPI_REJECTED");
        }
        return true;
    }
}
