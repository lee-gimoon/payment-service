package com.example.payment.payment.domain;

import java.math.BigDecimal;
import java.time.Instant;

public record PaymentResult(PaymentStatus status, String pgStatus, String errorCode, Instant approvedAt,
                            BigDecimal pgAmount, String pgCurrency) {
    public static PaymentResult unknown(String errorCode) {
        return new PaymentResult(PaymentStatus.UNKNOWN, null, errorCode, null, null, null);
    }

    public static PaymentResult failed(String errorCode) {
        return new PaymentResult(PaymentStatus.FAILED, null, errorCode, null, null, null);
    }

    public static PaymentResult reviewRequired(String errorCode) {
        return new PaymentResult(PaymentStatus.REVIEW_REQUIRED, null, errorCode, null, null, null);
    }
}
