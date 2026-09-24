package com.example.payment.payment;

/** 결제창을 연 시점부터 인증·승인까지의 시도별 결과다. */
public enum PaymentAttemptStatus {
    STARTED, AUTH_CANCELED, AUTH_FAILED, PROCESSING, SUCCEEDED, FAILED, CANCELED, REVIEW_REQUIRED
}
