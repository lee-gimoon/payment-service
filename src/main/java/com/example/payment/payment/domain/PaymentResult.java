package com.example.payment.payment.domain;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 토스 응답이나 통신 오류를 해석해 TossPaymentClient가 PaymentService에 전달하는 내부 결과 DTO다.
 * 서비스는 상태·오류·승인 및 취소 정보를 이 객체에서 읽어 DB에 저장한다.
 * 브라우저에 보내는 HTTP 응답 DTO는 OrderResponse이며, 이 record를 직접 반환하지 않는다.
 */
public record PaymentResult(PaymentStatus status, String pgStatus, String errorCode, Instant approvedAt,
                            BigDecimal pgAmount, String pgCurrency, Instant canceledAt) {
    /** 통신 오류나 불완전한 응답 등으로 결제 결과를 확정할 수 없을 때 UNKNOWN과 오류 코드를 담아 반환한다. */
    public static PaymentResult unknown(String errorCode) {
        return new PaymentResult(PaymentStatus.UNKNOWN, null, errorCode, null, null, null, null);
    }

    /** 토스가 결제 거절처럼 확정적인 실패를 알렸을 때 FAILED와 오류 코드를 담아 반환한다. */
    public static PaymentResult failed(String errorCode) {
        return new PaymentResult(PaymentStatus.FAILED, null, errorCode, null, null, null, null);
    }

    /** 자동으로 결제 상태를 확정할 수 없어 사람이 확인해야 할 때 REVIEW_REQUIRED와 사유 코드를 담아 반환한다. */
    public static PaymentResult reviewRequired(String errorCode) {
        return new PaymentResult(PaymentStatus.REVIEW_REQUIRED, null, errorCode, null, null, null, null);
    }
}
