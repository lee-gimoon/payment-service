package com.example.payment.payment;

import com.example.payment.order.OrderRepository;
import com.example.payment.order.OrderStatus;
import com.example.payment.order.PurchaseOrder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 거래·시도·주문의 결과를 한 DB 트랜잭션으로 맞춘다. */
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
    public Payment record(Payment payment, PaymentResult result) {
        Payment saved = payments.findById(payment.getId()).orElseThrow();
        saved.applyResult(result);
        attempts.findById(saved.getAttemptId()).orElseThrow().apply(saved.getStatus(), saved.getErrorCode());
        PurchaseOrder order = orders.findById(saved.getOrderId()).orElseThrow();
        if (saved.getStatus() == PaymentStatus.SUCCEEDED && order.getStatus() == OrderStatus.PENDING_PAYMENT) {
            order.confirm();
        } else if (saved.getStatus() == PaymentStatus.CANCELED) {
            order.cancel();
        }
        return saved;
    }
}
