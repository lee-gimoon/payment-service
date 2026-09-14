package com.example.payment.payment;

import java.time.Instant;

/** 토스 HTTP 응답을 우리 서비스의 성공·실패·확인 필요 상태로 바꾼 결과 DTO다. */
public record PaymentResult(PaymentStatus status, String pgStatus, String errorCode, Instant approvedAt) {
    public static PaymentResult unknown(String errorCode) {
        return new PaymentResult(PaymentStatus.UNKNOWN, null, errorCode, null);
    }

    public static PaymentResult failed(String errorCode) {
        return new PaymentResult(PaymentStatus.FAILED, null, errorCode, null);
    }
}
