package com.example.payment.order;

import com.example.payment.payment.Payment;
import com.example.payment.payment.PaymentStatus;
import java.time.Instant;

/** React가 받는 주문 응답이다. record는 데이터를 전달하는 DTO를 간단하게 작성하는 Java 문법이다. */
public record OrderResponse(String orderId, String productName, int quantity, long amount, String currency,
                            Instant createdAt, PaymentResponse payment) {

    /** 주문만 있으면 READY, 결제가 있으면 DB에 저장된 결제 결과를 담는다. */
    public static OrderResponse of(PurchaseOrder order, Payment payment) {
        PaymentResponse paymentResponse;
        if (payment == null) {
            paymentResponse = new PaymentResponse(PaymentStatus.READY, null, null, null, null, null,
                    "결제 대기 중입니다.", false);
        } else {
            paymentResponse = new PaymentResponse(
                    payment.getStatus(), payment.getOrderId(), payment.getPgStatus(),
                    payment.getApprovedAt(), payment.getCheckedAt(), payment.getErrorCode(),
                    message(payment.getStatus()), payment.canReconcile());
        }
        return new OrderResponse(order.getId(), order.getProductName(), order.getQuantity(),
                order.getAmount(), "KRW", order.getCreatedAt(), paymentResponse);
    }

    private static String message(PaymentStatus status) {
        return switch (status) {
            case READY -> "결제 대기 중입니다.";
            case PROCESSING -> "결제 결과를 기다리고 있습니다. 잠시 후 저장된 결과 조회 또는 PG 결과 재확인을 눌러주세요.";
            case SUCCEEDED -> "결제가 완료되었습니다.";
            case FAILED -> "결제가 거절되었거나 만료되었습니다. 새 주문으로 다시 시도할 수 있습니다.";
            case UNKNOWN -> "결과를 확인하지 못했습니다. 다시 결제하지 말고 PG 결과 재확인을 눌러주세요.";
        };
    }

    /** 프론트가 결제 기록의 존재를 확인하는 attemptId에는 주문번호를 재사용한다. paymentKey는 공개하지 않는다. */
    public record PaymentResponse(PaymentStatus status, String attemptId, String pgStatus, Instant approvedAt,
                                  Instant checkedAt, String errorCode, String message, boolean canReconcile) {}
}
