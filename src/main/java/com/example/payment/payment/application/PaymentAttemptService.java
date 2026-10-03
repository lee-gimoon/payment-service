package com.example.payment.payment.application;

import com.example.payment.api.error.ApiException;
import com.example.payment.order.domain.OrderItem;
import com.example.payment.order.domain.PurchaseOrder;
import com.example.payment.order.persistence.OrderItemRepository;
import com.example.payment.order.persistence.OrderRepository;
import com.example.payment.payment.domain.PaymentAttempt;
import com.example.payment.payment.domain.PaymentAttemptStatus;
import com.example.payment.payment.persistence.PaymentAttemptRepository;
import com.example.payment.product.ProductInventory;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentAttemptService {
    private final OrderRepository orders;
    private final OrderItemRepository orderItems;
    private final PaymentAttemptRepository attempts;
    private final ProductInventory inventory;

    public PaymentAttemptService(OrderRepository orders, OrderItemRepository orderItems,
                                 PaymentAttemptRepository attempts, ProductInventory inventory) {
        this.orders = orders;
        this.orderItems = orderItems;
        this.attempts = attempts;
        this.inventory = inventory;
    }

    @Transactional
    public AttemptResponse start(String orderId, String customerId) {
        PurchaseOrder order = orders.findByIdForUpdate(orderId)
                .filter(found -> found.isOwnedBy(customerId))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "주문을 찾을 수 없습니다."));
        if (order.isCanceled()) throw orderCanceled();
        if (!order.acceptsNewPayment()) {
            throw new ApiException(HttpStatus.CONFLICT, "ORDER_NOT_PAYABLE", "이 주문은 새 결제를 시작할 수 없습니다.");
        }
        // 배송지를 받기 전에 만든 주문은 보낼 곳이 없으므로 새로 결제하지 않는다.
        if (order.getShipping() == null) {
            throw new ApiException(HttpStatus.CONFLICT, "SHIPPING_ADDRESS_REQUIRED",
                    "배송지 없이 만든 이전 주문이라 결제할 수 없습니다. 상품을 다시 담아 새로 주문해주세요.");
        }
        // 품절된 주문은 결제창을 열기 전에 알린다. 재고는 승인 관문에서 가져간다.
        inventory.requireAvailable(orderItems.findByOrderId(orderId).stream().map(OrderItem::stockLine).toList());
        return AttemptResponse.of(attempts.save(PaymentAttempt.start(order)));
    }

    @Transactional
    public AttemptResponse authenticationResult(String attemptId, String customerId,
                                                AuthenticationResult request) {
        if (request.status() != PaymentAttemptStatus.AUTH_CANCELED && request.status() != PaymentAttemptStatus.AUTH_FAILED) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ATTEMPT_STATUS", "인증 취소 또는 실패만 기록할 수 있습니다.");
        }
        String orderId = attempts.findOrderIdById(attemptId).orElseThrow(PaymentAttemptService::attemptNotFound);
        // 승인 준비와 같은 순서(주문 → 시도)로 잠가 늦은 이벤트와 승인 요청을 줄 세운다.
        orders.findByIdForUpdate(orderId).filter(order -> order.isOwnedBy(customerId))
                .orElseThrow(PaymentAttemptService::attemptNotFound);
        PaymentAttempt attempt = attempts.findById(attemptId).orElseThrow(PaymentAttemptService::attemptNotFound);
        if (request.status() == PaymentAttemptStatus.AUTH_CANCELED) {
            attempt.authenticationCanceled(request.errorCode());
        } else {
            attempt.authenticationFailed(request.errorCode());
        }
        return AttemptResponse.of(attempt);
    }

    static ApiException orderCanceled() {
        return new ApiException(HttpStatus.CONFLICT, "ORDER_CANCELED",
                "결제 기한이 지나 취소된 주문입니다. 상품을 다시 담아 새로 주문해주세요.");
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
