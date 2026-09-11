package com.example.payment.payment;

import java.time.Instant;

public record PaymentOutcome(PaymentStatus status, String pgStatus, String errorCode, Instant approvedAt) {
    public static PaymentOutcome unknown(String code) {
        return new PaymentOutcome(PaymentStatus.UNKNOWN, null, code, null);
    }

    public static PaymentOutcome failed(String code) {
        return new PaymentOutcome(PaymentStatus.FAILED, null, code, null);
    }
}
