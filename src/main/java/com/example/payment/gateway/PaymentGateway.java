package com.example.payment.gateway;

import com.example.payment.payment.PaymentOutcome;

public interface PaymentGateway {
    PaymentOutcome confirm(PaymentCommand command);
    PaymentOutcome lookup(PaymentCommand command);

    record PaymentCommand(String orderId, String paymentKey, long amount, String attemptId, String operationId) {}
}
