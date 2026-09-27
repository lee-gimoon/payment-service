package com.example.payment.payment.application;

import com.example.payment.order.OrderRepository;
import com.example.payment.order.OrderStatus;
import com.example.payment.payment.domain.Payment;
import com.example.payment.payment.domain.PaymentResult;
import com.example.payment.payment.domain.PaymentStatus;
import com.example.payment.payment.persistence.PaymentAttemptRepository;
import com.example.payment.payment.persistence.PaymentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentSettlementService {
    private final PaymentRepository payments;
    private final PaymentAttemptRepository attempts;
    private final OrderRepository orders;

    public PaymentSettlementService(PaymentRepository payments, PaymentAttemptRepository attempts,
                                    OrderRepository orders) {
        this.payments = payments;
        this.attempts = attempts;
        this.orders = orders;
    }

    @Transactional
    public void record(String paymentId, PaymentResult result) {
        Payment payment = payments.findById(paymentId).orElseThrow();
        payment.applyResult(result);
        attempts.findById(payment.getAttemptId()).orElseThrow().apply(result.status(), result.errorCode());
        if (result.status() == PaymentStatus.SUCCEEDED) {
            var order = orders.findById(payment.getOrderId()).orElseThrow();
            if (order.getStatus() == OrderStatus.PENDING_PAYMENT) {
                order.confirm();
            }
        }
    }
}
