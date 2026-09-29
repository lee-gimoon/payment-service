package com.example.payment.payment.application;

import com.example.payment.api.error.ApiException;
import com.example.payment.order.OrderRepository;
import com.example.payment.order.PurchaseOrder;
import com.example.payment.payment.domain.PaymentAttempt;
import com.example.payment.payment.domain.PaymentAttemptStatus;
import com.example.payment.payment.infrastructure.toss.TossProperties;
import com.example.payment.payment.persistence.PaymentAttemptRepository;
import java.math.BigDecimal;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 서버 승인 관문. 주문 잠금 아래에서 승인 슬롯을 잡고 APPROVING을 커밋한 뒤에만 PG 승인을 호출하게 한다. */
@Service
public class PaymentPreparationService {
    private final OrderRepository orders;
    private final PaymentAttemptRepository attempts;
    private final TossProperties tossProperties;

    public PaymentPreparationService(OrderRepository orders, PaymentAttemptRepository attempts,
                                     TossProperties tossProperties) {
        this.orders = orders;
        this.attempts = attempts;
        this.tossProperties = tossProperties;
    }

    @Transactional
    public Prepared prepare(String orderId, String customerId, String paymentKey, String attemptId,
                            BigDecimal amount) {
        PurchaseOrder order = orders.findByIdForUpdate(orderId)
                .filter(found -> found.isOwnedBy(customerId))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "주문을 찾을 수 없습니다."));
        if (amount.compareTo(BigDecimal.valueOf(order.getAmount())) != 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "AMOUNT_MISMATCH", "주문 금액과 결제 금액이 다릅니다.");
        }

        PaymentAttempt existing = attempts.findByPaymentKey(paymentKey).orElse(null);
        if (existing != null) {
            if (!existing.getOrderId().equals(orderId) || !existing.getId().equals(attemptId)) {
                throw new ApiException(HttpStatus.CONFLICT, "PAYMENT_CONFLICT", "다른 주문 또는 시도에 연결된 결제 키입니다.");
            }
            return new Prepared(existing, false);
        }
        if (!order.acceptsNewPayment()) {
            throw new ApiException(HttpStatus.CONFLICT, "PAYMENT_CONFLICT", "이 주문은 다른 결제가 진행 중이거나 완료되었습니다.");
        }
        if (!tossProperties.configured()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "PAYMENT_NOT_CONFIGURED", "토스 테스트 키를 설정해주세요.");
        }

        PaymentAttempt attempt = attempts.findById(attemptId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ATTEMPT_NOT_FOUND", "결제 시도를 찾을 수 없습니다."));
        if (!attempt.getOrderId().equals(orderId) || attempt.getStatus() != PaymentAttemptStatus.STARTED) {
            throw new ApiException(HttpStatus.CONFLICT, "ATTEMPT_CONFLICT", "진행할 수 없는 결제 시도입니다.");
        }

        order.claimApproval(attempt.getId());
        attempt.requestApproval(paymentKey);
        return new Prepared(attempt, true);
    }

    public record Prepared(PaymentAttempt attempt, boolean newApproval) {}
}
