package com.example.payment.payment.domain;

import java.math.BigDecimal;
import java.time.Instant;

/** PG 응답을 분류한 결과와 그 근거. 시도의 상태 변경은 도메인이 결정한다. */
public record PaymentResult(Outcome outcome, String pgStatus, String errorCode, Instant approvedAt,
                            BigDecimal pgAmount, String pgCurrency) {
    public enum Outcome { SUCCEEDED, FAILED, UNKNOWN, REVIEW_REQUIRED }

    public static PaymentResult unknown(String errorCode) {
        return new PaymentResult(Outcome.UNKNOWN, null, errorCode, null, null, null);
    }

    public static PaymentResult failed(String errorCode) {
        return new PaymentResult(Outcome.FAILED, null, errorCode, null, null, null);
    }

    public static PaymentResult reviewRequired(String errorCode) {
        return new PaymentResult(Outcome.REVIEW_REQUIRED, null, errorCode, null, null, null);
    }
}
