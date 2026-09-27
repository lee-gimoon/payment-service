package com.example.payment.order;

import com.example.payment.payment.domain.Payment;
import com.example.payment.payment.domain.PaymentAttempt;
import com.example.payment.payment.domain.PaymentAttemptStatus;
import com.example.payment.payment.domain.PaymentStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OrderResponse(String orderId, String productName, int quantity, long amount, String currency,
                            List<ItemResponse> items, Instant createdAt, OrderStatus status,
                            AttemptResponse latestAttempt, PaymentResponse payment) {

    public static OrderResponse of(PurchaseOrder order, List<OrderItem> orderItems, Payment payment,
                                   PaymentAttempt attempt) {
        PaymentResponse paymentResponse;
        if (payment == null) {
            paymentResponse = new PaymentResponse(PaymentStatus.READY, null, null, null, null,
                    "결제 대기 중입니다.", null, null);
        } else {
            paymentResponse = new PaymentResponse(
                    payment.getStatus(), payment.getPgStatus(),
                    payment.getApprovedAt(), payment.getCheckedAt(), payment.getErrorCode(),
                    message(payment.getStatus()), payment.getPgAmount(), payment.getPgCurrency());
        }
        List<ItemResponse> items = orderItems.stream().map(item ->
                new ItemResponse(item.getProductId(), item.getProductName(), item.getSize(),
                        item.getUnitPrice(), item.getQuantity())).toList();
        return new OrderResponse(order.getId(), order.getProductName(), order.getQuantity(),
                order.getAmount(), order.getCurrency(), items, order.getCreatedAt(), order.getStatus(),
                attempt == null ? null : new AttemptResponse(attempt.getId(), attempt.getStatus(),
                        attempt.getStartedAt(), attempt.getFinishedAt(), attempt.getErrorCode()), paymentResponse);
    }

    private static String message(PaymentStatus status) {
        return switch (status) {
            case READY -> "결제 대기 중입니다.";
            case PROCESSING -> "결제를 처리하고 있습니다. 오래 지속되면 주문번호로 고객센터에 문의해주세요.";
            case SUCCEEDED -> "결제가 완료되었습니다.";
            case FAILED -> "결제가 거절되었거나 만료되었습니다. 다시 시도할 수 있습니다.";
            case UNKNOWN -> "결제 결과를 확인하지 못했습니다. 다시 결제하지 말고 주문번호로 고객센터에 문의해주세요.";
            case REVIEW_REQUIRED -> "결제 확인이 지연되고 있습니다. 다시 결제하지 말고 주문번호로 고객센터에 문의해주세요.";
        };
    }

    public record ItemResponse(String productId, String productName, String size, long unitPrice, int quantity) {}

    public record AttemptResponse(String id, PaymentAttemptStatus status, Instant startedAt,
                                  Instant finishedAt, String errorCode) {}

    public record PaymentResponse(PaymentStatus status, String pgStatus, Instant approvedAt,
                                  Instant checkedAt, String errorCode, String message,
                                  BigDecimal paidAmount, String paidCurrency) {}
}
