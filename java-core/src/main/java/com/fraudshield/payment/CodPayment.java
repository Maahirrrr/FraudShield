package com.fraudshield.payment;

import com.fraudshield.exception.PaymentFailedException;
import com.fraudshield.model.Order;

public class CodPayment extends Payment {
    private final String confirmationPhone;

    public CodPayment(String paymentId, String confirmationPhone) {
        super(paymentId, "CASH_ON_DELIVERY");
        this.confirmationPhone = confirmationPhone;
    }

    public String getConfirmationPhone() { return confirmationPhone; }

    @Override
    public boolean process(Order order) throws PaymentFailedException {
        if (order.getTotalAmount() > 15000.0) {
            throw new PaymentFailedException("Cash-on-Delivery is restricted for orders exceeding ₹15,000.", "ERR_COD_LIMIT_EXCEEDED");
        }
        return true;
    }
}
