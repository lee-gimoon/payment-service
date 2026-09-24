package com.example.payment.payment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payment_attempts")
public class PaymentAttempt {
    @Id
    @Column(length = 36)
    private String id;

    @Column(nullable = false, length = 64)
    private String orderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private PaymentAttemptStatus status;

    @Column(nullable = false)
    private Instant startedAt;

    private Instant finishedAt;

    @Column(length = 80)
    private String errorCode;

    @Version
    private Long version;

    protected PaymentAttempt() {}

    public PaymentAttempt(String orderId) {
        this.id = UUID.randomUUID().toString();
        this.orderId = orderId;
        this.status = PaymentAttemptStatus.STARTED;
        this.startedAt = Instant.now();
    }

    public void authenticationCanceled(String code) {
        finishAuthentication(PaymentAttemptStatus.AUTH_CANCELED, code);
    }

    public void authenticationFailed(String code) {
        finishAuthentication(PaymentAttemptStatus.AUTH_FAILED, code);
    }

    private void finishAuthentication(PaymentAttemptStatus outcome, String code) {
        if (status != PaymentAttemptStatus.STARTED) return;
        status = outcome;
        errorCode = code;
        finishedAt = Instant.now();
    }

    public void processing() {
        if (status != PaymentAttemptStatus.STARTED) {
            throw new IllegalStateException("시작된 결제 시도만 승인할 수 있습니다.");
        }
        status = PaymentAttemptStatus.PROCESSING;
    }

    public void apply(PaymentStatus paymentStatus, String code) {
        status = switch (paymentStatus) {
            case PROCESSING, UNKNOWN, CANCEL_PENDING -> PaymentAttemptStatus.PROCESSING;
            case SUCCEEDED -> PaymentAttemptStatus.SUCCEEDED;
            case FAILED -> PaymentAttemptStatus.FAILED;
            case CANCELED -> PaymentAttemptStatus.CANCELED;
            case REVIEW_REQUIRED -> PaymentAttemptStatus.REVIEW_REQUIRED;
            case READY -> throw new IllegalArgumentException("거래 없는 상태는 결제 시도 결과가 아닙니다.");
        };
        errorCode = code;
        if (status != PaymentAttemptStatus.PROCESSING) finishedAt = Instant.now();
    }

    public String getId() { return id; }
    public String getOrderId() { return orderId; }
    public PaymentAttemptStatus getStatus() { return status; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getFinishedAt() { return finishedAt; }
    public String getErrorCode() { return errorCode; }
}
