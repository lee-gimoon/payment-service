package com.example.payment.payment.application;

import com.example.payment.api.error.ApiException;
import com.example.payment.order.OrderRepository;
import com.example.payment.order.OrderStatus;
import com.example.payment.order.PurchaseOrder;
import com.example.payment.payment.domain.Payment;
import com.example.payment.payment.domain.PaymentAttempt;
import com.example.payment.payment.domain.PaymentAttemptStatus;
import com.example.payment.payment.domain.PaymentStatus;
import com.example.payment.payment.persistence.PaymentAttemptRepository;
import com.example.payment.payment.persistence.PaymentRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** PG 호출 전에 주문 잠금 아래에서 한 거래만 생성한다. 트랜잭션은 PG 호출 전에 끝난다. */
@Service
public class PaymentPreparationService {
    private final OrderRepository orders;
    private final PaymentRepository payments;
    private final PaymentAttemptRepository attempts;

    public PaymentPreparationService(OrderRepository orders, PaymentRepository payments,
                                     PaymentAttemptRepository attempts) {
        this.orders = orders;
        this.payments = payments;
        this.attempts = attempts;
    }

    @Transactional
    public Prepared prepare(String orderId, String paymentKey, String attemptId) {
        PurchaseOrder order = orders.findByIdForUpdate(orderId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "주문을 찾을 수 없습니다."));
        Payment sameKey = payments.findByPaymentKey(paymentKey).orElse(null);
        if (sameKey != null) {
            if (!sameKey.getOrderId().equals(orderId)
                    || (attemptId != null && !sameKey.getAttemptId().equals(attemptId))) {
                throw new ApiException(HttpStatus.CONFLICT, "PAYMENT_CONFLICT", "결제 키가 다른 주문 또는 시도에 연결되어 있습니다.");
            }
            return new Prepared(sameKey, false);
        }
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT
                || payments.findFirstByOrderIdAndStatusNotOrderByCreatedAtDescIdDesc(orderId, PaymentStatus.FAILED).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "PAYMENT_CONFLICT", "이 주문은 다른 결제가 진행 중이거나 완료되었습니다.");
        }
        PaymentAttempt attempt;
        if (attemptId == null) {
            attempt = attempts.save(new PaymentAttempt(orderId));
        } else {
            attempt = attempts.findById(attemptId)
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ATTEMPT_NOT_FOUND", "결제 시도를 찾을 수 없습니다."));
            if (!attempt.getOrderId().equals(orderId) || attempt.getStatus() != PaymentAttemptStatus.STARTED) {
                throw new ApiException(HttpStatus.CONFLICT, "ATTEMPT_CONFLICT", "진행할 수 없는 결제 시도입니다.");
            }
        }
        if (payments.findByAttemptId(attempt.getId()).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "ATTEMPT_CONFLICT", "이미 결제가 연결된 시도입니다.");
        }
        Payment payment = payments.saveAndFlush(new Payment(orderId, paymentKey, attempt.getId(), order.getAmount()));
        attempt.processing();
        return new Prepared(payment, true);
    }

    public record Prepared(Payment payment, boolean newPayment) {}
}
