package com.example.payment.payment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payments")
public class Payment {
    @Id
    @Column(length = 64)
    private String orderId;
    @Column(nullable = false, unique = true, length = 36)
    private String attemptId;
    @Column(nullable = false, unique = true, length = 200)
    private String paymentKey;
    @Column(nullable = false)
    private long amount;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status;
    @Column(nullable = false, length = 36)
    private String operationId;
    @Column(nullable = false)
    private Instant processingUntil;
    @Column(nullable = false)
    private Instant createdAt;
    private Instant checkedAt;
    private Instant approvedAt;
    @Column(length = 40)
    private String pgStatus;
    @Column(length = 80)
    private String errorCode;

    protected Payment() {}

    public static Payment start(String orderId, String paymentKey, long amount, Instant now, Duration lease) {
        Payment payment = new Payment();
        payment.orderId = orderId;
        payment.paymentKey = paymentKey;
        payment.amount = amount;
        payment.attemptId = UUID.randomUUID().toString();
        payment.createdAt = now;
        payment.beginOperation(now, lease);
        return payment;
    }

    public void beginOperation(Instant now, Duration lease) {
        this.operationId = UUID.randomUUID().toString();
        this.status = PaymentStatus.PROCESSING;
        this.processingUntil = now.plus(lease);
    }

    public PaymentStatus visibleStatus(Instant now) {
        return status == PaymentStatus.PROCESSING && !now.isBefore(processingUntil)
                ? PaymentStatus.UNKNOWN : status;
    }

    public boolean canReconcile(Instant now) {
        return visibleStatus(now) == PaymentStatus.UNKNOWN;
    }

    public void complete(String operationId, PaymentOutcome outcome, Instant now) {
        // 만료된 작업의 늦은 응답이 새 조회 결과를 덮어쓰지 않도록 작업 소유권을 검사한다.
        if (status != PaymentStatus.PROCESSING || !this.operationId.equals(operationId)) {
            return;
        }
        status = outcome.status();
        pgStatus = outcome.pgStatus();
        errorCode = outcome.errorCode();
        approvedAt = outcome.approvedAt();
        checkedAt = now;
    }

    public String getOrderId() { return orderId; }
    public String getAttemptId() { return attemptId; }
    public String getPaymentKey() { return paymentKey; }
    public long getAmount() { return amount; }
    public String getOperationId() { return operationId; }
    public Instant getCheckedAt() { return checkedAt; }
    public Instant getApprovedAt() { return approvedAt; }
    public String getPgStatus() { return pgStatus; }
    public String getErrorCode() { return errorCode; }
}
