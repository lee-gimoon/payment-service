package com.example.payment.payment.application;

import com.example.payment.api.error.ApiException;
import com.example.payment.order.OrderRepository;
import com.example.payment.order.PurchaseOrder;
import com.example.payment.payment.domain.PaymentAttempt;
import com.example.payment.payment.domain.PaymentAttemptStatus;
import com.example.payment.payment.persistence.PaymentAttemptRepository;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentAttemptService {
    private final OrderRepository orders;
    private final PaymentAttemptRepository attempts;

    public PaymentAttemptService(OrderRepository orders, PaymentAttemptRepository attempts) {
        this.orders = orders;
        this.attempts = attempts;
    }

    @Transactional
    public AttemptResponse start(String orderId) {
        PurchaseOrder order = orders.findByIdForUpdate(orderId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "주문을 찾을 수 없습니다."));
        if (!order.acceptsNewPayment()) {
            throw new ApiException(HttpStatus.CONFLICT, "ORDER_NOT_PAYABLE", "이 주문은 새 결제를 시작할 수 없습니다.");
        }
        return AttemptResponse.of(attempts.save(PaymentAttempt.start(order)));
    }

    @Transactional
    public AttemptResponse authenticationResult(String attemptId, AuthenticationResult request) {
        if (request.status() != PaymentAttemptStatus.AUTH_CANCELED && request.status() != PaymentAttemptStatus.AUTH_FAILED) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ATTEMPT_STATUS", "인증 취소 또는 실패만 기록할 수 있습니다.");
        }
        String orderId = attempts.findOrderIdById(attemptId).orElseThrow(PaymentAttemptService::attemptNotFound);
        // 승인 준비와 같은 순서(주문 → 시도)로 잠가 늦은 이벤트와 승인 요청을 줄 세운다.
        orders.findByIdForUpdate(orderId).orElseThrow();
        PaymentAttempt attempt = attempts.findById(attemptId).orElseThrow(PaymentAttemptService::attemptNotFound);
        if (request.status() == PaymentAttemptStatus.AUTH_CANCELED) {
            attempt.authenticationCanceled(request.errorCode());
        } else {
            attempt.authenticationFailed(request.errorCode());
        }
        return AttemptResponse.of(attempt);
    }

    private static ApiException attemptNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "ATTEMPT_NOT_FOUND", "결제 시도를 찾을 수 없습니다.");
    }

    public record AttemptResponse(String id, String orderId, PaymentAttemptStatus status) {
        static AttemptResponse of(PaymentAttempt attempt) {
            return new AttemptResponse(attempt.getId(), attempt.getOrderId(), attempt.getStatus());
        }
    }

    public record AuthenticationResult(@NotNull PaymentAttemptStatus status, @Size(max = 80) String errorCode) {}
}
