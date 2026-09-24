package com.example.payment.payment;

import com.example.payment.api.error.ApiException;
import com.example.payment.order.OrderRepository;
import com.example.payment.order.OrderStatus;
import com.example.payment.order.PurchaseOrder;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentAttemptService {
    private final OrderRepository orders;
    private final PaymentRepository payments;
    private final PaymentAttemptRepository attempts;

    public PaymentAttemptService(OrderRepository orders, PaymentRepository payments,
                                 PaymentAttemptRepository attempts) {
        this.orders = orders;
        this.payments = payments;
        this.attempts = attempts;
    }

    @Transactional
    public AttemptResponse start(String orderId) {
        PurchaseOrder order = orders.findByIdForUpdate(orderId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "주문을 찾을 수 없습니다."));
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT
                || payments.findFirstByOrderIdAndStatusNotOrderByCreatedAtDescIdDesc(orderId, PaymentStatus.FAILED).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "ORDER_NOT_PAYABLE", "이 주문은 새 결제를 시작할 수 없습니다.");
        }
        return AttemptResponse.of(attempts.save(new PaymentAttempt(orderId)));
    }

    @Transactional
    public AttemptResponse authenticationResult(String attemptId, AuthenticationResult request) {
        PaymentAttempt attempt = attempts.findById(attemptId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ATTEMPT_NOT_FOUND", "결제 시도를 찾을 수 없습니다."));
        if (request.status() == PaymentAttemptStatus.AUTH_CANCELED) {
            attempt.authenticationCanceled(request.errorCode());
        } else if (request.status() == PaymentAttemptStatus.AUTH_FAILED) {
            attempt.authenticationFailed(request.errorCode());
        } else {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ATTEMPT_STATUS", "인증 취소 또는 실패만 기록할 수 있습니다.");
        }
        return AttemptResponse.of(attempt);
    }

    public record AttemptResponse(String id, String orderId, PaymentAttemptStatus status) {
        static AttemptResponse of(PaymentAttempt attempt) {
            return new AttemptResponse(attempt.getId(), attempt.getOrderId(), attempt.getStatus());
        }
    }

    public record AuthenticationResult(@NotNull PaymentAttemptStatus status, @Size(max = 80) String errorCode) {}
}
