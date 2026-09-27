package com.example.payment.payment.domain;

public enum PaymentAttemptStatus {
    STARTED,
    AUTH_CANCELED,
    AUTH_FAILED,
    PROCESSING,
    SUCCEEDED,
    FAILED,
    REVIEW_REQUIRED
}
