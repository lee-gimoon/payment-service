/* 파일 역할: 주문·결제 엔티티를 React에 반환할 API 응답 데이터로 변환한다. */
package com.example.payment.order;

import com.example.payment.payment.Payment;
import com.example.payment.payment.PaymentStatus;
import java.time.Instant;

/** 주문 내용과 결제 요약을 함께 전달하는 불변 DTO다. record는 생성자와 필드 접근 메서드를 제공한다. */
public record OrderResponse(String orderId, String productName, int quantity, long amount, String currency,
                            Instant createdAt, PaymentResponse payment) {
    /**
     * 결제 기록이 없으면 READY, 있으면 현재 시각 기준 상태와 재확인 가능 여부를 응답에 담는다.
     * 표시 상태를 계산하는 과정은 DB 값을 변경하지 않으며, paymentKey도 응답에 포함하지 않는다.
     */
    public static OrderResponse of(PurchaseOrder order, Payment payment, Instant now) {
        PaymentStatus status = payment == null ? PaymentStatus.READY : payment.visibleStatus(now);
        PaymentResponse result = new PaymentResponse(status, payment == null ? null : payment.getAttemptId(),
                payment == null ? null : payment.getPgStatus(), payment == null ? null : payment.getApprovedAt(),
                payment == null ? null : payment.getCheckedAt(), payment == null ? null : payment.getErrorCode(),
                message(status), payment != null && payment.canReconcile(now));
        return new OrderResponse(order.getId(), order.getProductName(), order.getQuantity(), order.getAmount(),
                order.getCurrency(), order.getCreatedAt(), result);
    }

    /** 결제 상태에 따라 화면에 표시할 안내와 사용자가 이어서 할 행동을 선택한다. */
    private static String message(PaymentStatus status) {
        return switch (status) {
            case READY -> "결제 대기 중입니다.";
            case PROCESSING -> "결제 결과를 처리하고 있습니다. 잠시 후 주문을 다시 조회해주세요.";
            case SUCCEEDED -> "결제가 완료되었습니다.";
            case FAILED -> "결제가 거절되었거나 만료되었습니다. 새 주문으로 다시 시도할 수 있습니다.";
            case UNKNOWN -> "결제 결과 확인이 필요합니다. 다시 결제하지 말고 PG 결과 재확인을 눌러주세요.";
        };
    }

    /** 결제 상태, 처리 시각, 오류 코드와 재확인 가능 여부를 담는 주문 응답의 하위 DTO다. */
    public record PaymentResponse(PaymentStatus status, String attemptId, String pgStatus, Instant approvedAt,
                                  Instant checkedAt, String errorCode, String message, boolean canReconcile) {}
}
