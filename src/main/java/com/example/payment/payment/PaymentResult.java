package com.example.payment.payment;

import java.time.Instant;
import java.math.BigDecimal;

/** 토스 HTTP 응답을 우리 서비스의 성공·실패·확인 필요 상태로 바꾼 결과 DTO다. */
public record PaymentResult(PaymentStatus status, String pgStatus, String errorCode, Instant approvedAt,
                            BigDecimal pgAmount, String pgCurrency, Instant canceledAt) {
    public PaymentResult(PaymentStatus status, String pgStatus, String errorCode, Instant approvedAt) {
        this(status, pgStatus, errorCode, approvedAt, null, null, null);
    }

    /** 결제 성공·실패를 아직 확정할 수 없을 때 결과 미확정 객체를 만든다. */
    public static PaymentResult unknown(String errorCode) {
        return new PaymentResult(PaymentStatus.UNKNOWN, null, errorCode, null);
    }

    public static PaymentResult failed(String errorCode) {
        return new PaymentResult(PaymentStatus.FAILED, null, errorCode, null);
    }

    public static PaymentResult reviewRequired(String errorCode) {
        return new PaymentResult(PaymentStatus.REVIEW_REQUIRED, null, errorCode, null);
    }
}
