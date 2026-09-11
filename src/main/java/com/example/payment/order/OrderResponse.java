package com.example.payment.order;

import com.example.payment.payment.Payment;
import com.example.payment.payment.PaymentStatus;
import java.time.Instant;

public record OrderResponse(String orderId, String productName, int quantity, long amount, String currency,
                            Instant createdAt, PaymentResponse payment) {
    public static OrderResponse of(PurchaseOrder order, Payment payment, Instant now) {
        PaymentStatus status = payment == null ? PaymentStatus.READY : payment.visibleStatus(now);
        PaymentResponse result = new PaymentResponse(status, payment == null ? null : payment.getAttemptId(),
                payment == null ? null : payment.getPgStatus(), payment == null ? null : payment.getApprovedAt(),
                payment == null ? null : payment.getCheckedAt(), payment == null ? null : payment.getErrorCode(),
                message(status), payment != null && payment.canReconcile(now));
        return new OrderResponse(order.getId(), order.getProductName(), order.getQuantity(), order.getAmount(),
                order.getCurrency(), order.getCreatedAt(), result);
    }

    private static String message(PaymentStatus status) {
        return switch (status) {
            case READY -> "결제 대기 중입니다.";
            case PROCESSING -> "결제 결과를 처리하고 있습니다. 잠시 후 주문을 다시 조회해주세요.";
            case SUCCEEDED -> "결제가 완료되었습니다.";
            case FAILED -> "결제가 거절되었거나 만료되었습니다. 새 주문으로 다시 시도할 수 있습니다.";
            case UNKNOWN -> "결제 결과 확인이 필요합니다. 다시 결제하지 말고 PG 결과 재확인을 눌러주세요.";
        };
    }

    public record PaymentResponse(PaymentStatus status, String attemptId, String pgStatus, Instant approvedAt,
                                  Instant checkedAt, String errorCode, String message, boolean canReconcile) {}
}
