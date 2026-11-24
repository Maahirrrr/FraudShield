package com.fraudshield.service;

import com.fraudshield.exception.PaymentFailedException;
import com.fraudshield.model.Order;
import com.fraudshield.payment.Payment;

public class PaymentService {
    public boolean executePayment(Order order, Payment payment) throws PaymentFailedException {
        return payment.process(order);
    }
}
