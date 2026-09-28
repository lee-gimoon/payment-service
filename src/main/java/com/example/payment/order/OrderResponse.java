package com.example.payment.order;

import com.example.payment.payment.domain.PaymentAttempt;
import com.example.payment.payment.domain.PaymentAttemptStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OrderResponse(String orderId, String productName, int quantity, long amount, String currency,
                            List<ItemResponse> items, Instant createdAt, OrderStatus status,
                            AttemptResponse latestAttempt, PaymentResponse payment) {

    /**
     * @param approval 가장 최근에 승인을 요청한 시도. 아직 없으면 결제 대기로 표시한다.
     * @param latest 가장 최근에 생성한 시도. 인증 취소·실패도 포함한다.
     */
    public static OrderResponse of(PurchaseOrder order, List<OrderItem> orderItems, PaymentAttempt approval,
                                   PaymentAttempt latest) {
        PaymentResponse paymentResponse;
        if (approval == null) {
            paymentResponse = new PaymentResponse(PaymentState.READY, null, null, null, null,
                    message(PaymentState.READY), null, null);
        } else {
            PaymentState state = PaymentState.of(approval.getStatus());
            paymentResponse = new PaymentResponse(state, approval.getPgStatus(), approval.getPgApprovedAt(),
                    approval.getLastCheckedAt(), approval.getErrorCode(), message(state),
                    approval.getPgAmount(), approval.getPgCurrency());
        }
        List<ItemResponse> items = orderItems.stream().map(item ->
                new ItemResponse(item.getProductId(), item.getProductName(), item.getSize(),
                        item.getUnitPrice(), item.getQuantity())).toList();
        return new OrderResponse(order.getId(), order.getProductName(), order.getQuantity(),
                order.getAmount(), order.getCurrency(), items, order.getCreatedAt(), order.getStatus(),
                latest == null ? null : new AttemptResponse(latest.getId(), latest.getStatus(),
                        latest.getStartedAt(), latest.getFinishedAt(), latest.getErrorCode()), paymentResponse);
    }

    private static String message(PaymentState state) {
        return switch (state) {
            case READY -> "결제 대기 중입니다.";
            case APPROVING -> "결제를 승인하고 있습니다. 다시 결제하지 말고 잠시 후 주문 내역을 새로고침해주세요.";
            case SUCCEEDED -> "결제가 완료되었습니다.";
            case FAILED -> "결제가 거절되었거나 만료되었습니다. 다시 시도할 수 있습니다.";
            case UNKNOWN -> "결제 결과를 확인하고 있습니다. 다시 결제하지 말고 잠시 후 주문 내역을 새로고침해주세요.";
            case REVIEW_REQUIRED -> "결제 확인이 지연되고 있습니다. 다시 결제하지 말고 주문번호로 고객센터에 문의해주세요.";
        };
    }

    /** 주문의 결제 진행 상태. READY는 승인을 아직 요청하지 않은 주문을 표시하는 응답 값이다. */
    public enum PaymentState {
        READY, APPROVING, SUCCEEDED, FAILED, UNKNOWN, REVIEW_REQUIRED;

        static PaymentState of(PaymentAttemptStatus status) {
            return switch (status) {
                case APPROVING -> APPROVING;
                case UNKNOWN -> UNKNOWN;
                case REVIEW_REQUIRED -> REVIEW_REQUIRED;
                case SUCCEEDED -> SUCCEEDED;
                case FAILED -> FAILED;
                case STARTED, AUTH_CANCELED, AUTH_FAILED ->
                        throw new IllegalArgumentException("승인을 요청하지 않은 시도입니다: " + status);
            };
        }
    }

    public record ItemResponse(String productId, String productName, String size, long unitPrice, int quantity) {}

    public record AttemptResponse(String id, PaymentAttemptStatus status, Instant startedAt,
                                  Instant finishedAt, String errorCode) {}

    public record PaymentResponse(PaymentState status, String pgStatus, Instant approvedAt,
                                  Instant checkedAt, String errorCode, String message,
                                  BigDecimal paidAmount, String paidCurrency) {}
}
