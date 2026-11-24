package com.fraudshield.payment;

import com.fraudshield.exception.PaymentFailedException;
import com.fraudshield.model.Order;

public class CardPayment extends Payment {
    private final String cardToken;
    private final String last4;

    public CardPayment(String paymentId, String cardToken, String last4) {
        super(paymentId, "CREDIT_CARD");
        this.cardToken = cardToken;
        this.last4 = last4;
    }

    public String getCardToken() { return cardToken; }
    public String getLast4() { return last4; }

    @Override
    public boolean process(Order order) throws PaymentFailedException {
        // Test sandbox payment simulation
        if (cardToken == null || cardToken.trim().isEmpty()) {
            throw new PaymentFailedException("Invalid card token provided.", "ERR_TOKEN_MISSING");
        }
        if (cardToken.endsWith("0000")) {
            throw new PaymentFailedException("Card declined by issuing bank (insufficient funds / blocked card).", "ERR_CARD_DECLINED");
        }
        return true;
    }
}
