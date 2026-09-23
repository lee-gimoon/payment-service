package com.example.payment.order;

import com.example.payment.payment.Payment;
import com.example.payment.payment.PaymentStatus;
import java.time.Instant;
import java.math.BigDecimal;

/** React가 받는 주문 응답이다. record는 데이터를 전달하는 DTO를 간단하게 작성하는 Java 문법이다. */
public record OrderResponse(String orderId, String productName, int quantity, long amount, String currency,
                            Instant createdAt, PaymentResponse payment) {

    /** 주문만 있으면 READY, 결제가 있으면 DB에 저장된 결제 결과를 담는다. */
    public static OrderResponse of(PurchaseOrder order, Payment payment) {
        PaymentResponse paymentResponse;
        if (payment == null) {
            paymentResponse = new PaymentResponse(PaymentStatus.READY, null, null, null, null,
                    "결제 대기 중입니다.", null, null, null);
        } else {
            paymentResponse = new PaymentResponse(
                    payment.getStatus(), payment.getPgStatus(),
                    payment.getApprovedAt(), payment.getCheckedAt(), payment.getErrorCode(),
                    message(payment.getStatus()), payment.getPgAmount(), payment.getPgCurrency(), payment.getCanceledAt());
        }
        return new OrderResponse(order.getId(), order.getProductName(), order.getQuantity(),
                order.getAmount(), "KRW", order.getCreatedAt(), paymentResponse);
    }

    private static String message(PaymentStatus status) {
        return switch (status) {
            case READY -> "결제 대기 중입니다.";
            case PROCESSING -> "결제를 처리하고 있습니다. 결과가 자동으로 반영됩니다.";
            case SUCCEEDED -> "결제가 완료되었습니다.";
            case FAILED -> "결제가 거절되었거나 만료되었습니다. 새 주문으로 다시 시도할 수 있습니다.";
            case UNKNOWN -> "결제 결과를 확인하고 있습니다. 다시 결제하지 않아도 결과가 자동으로 반영됩니다.";
            case CANCEL_PENDING -> "주문과 결제 정보가 일치하지 않아 결제를 취소하고 있습니다.";
            case CANCELED -> "결제가 취소되었습니다. 환불 반영 시점은 결제수단에 따라 다릅니다.";
            case REVIEW_REQUIRED -> "결제 확인이 지연되고 있습니다. 다시 결제하지 말고 주문번호로 고객센터에 문의해주세요.";
        };
    }

    /** 결제 상태와 결과만 반환한다. 토스 paymentKey는 서버에 보관한다. */
    public record PaymentResponse(PaymentStatus status, String pgStatus, Instant approvedAt,
                                  Instant checkedAt, String errorCode, String message,
                                  BigDecimal paidAmount, String paidCurrency, Instant canceledAt) {}
}
