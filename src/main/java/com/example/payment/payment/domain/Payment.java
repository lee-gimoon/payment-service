package com.example.payment.payment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payments")
public class Payment {
    @Id
    @Column(length = 36)
    private String id;

    @Column(nullable = false, length = 64)
    private String orderId;

    @Column(nullable = false, unique = true, length = 36)
    private String attemptId;

    @Column(nullable = false, unique = true, length = 200)
    private String paymentKey;

    @Column(nullable = false)
    private long requestedAmount;

    @Column(nullable = false, length = 3)
    private String requestedCurrency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant approvedAt;
    private Instant checkedAt;

    @Column(precision = 24, scale = 6)
    private BigDecimal pgAmount;

    @Column(length = 3)
    private String pgCurrency;

    @Column(length = 40)
    private String pgStatus;

    @Column(length = 80)
    private String errorCode;

    @Version
    private Long version;

    protected Payment() {}

    public Payment(String orderId, String paymentKey, String attemptId, long requestedAmount) {
        if (requestedAmount <= 0) throw new IllegalArgumentException("결제 금액이 올바르지 않습니다.");
        this.id = UUID.randomUUID().toString();
        this.orderId = orderId;
        this.paymentKey = paymentKey;
        this.attemptId = attemptId;
        this.requestedAmount = requestedAmount;
        this.requestedCurrency = "KRW";
        this.status = PaymentStatus.PROCESSING;
        this.createdAt = Instant.now();
    }

    public void applyResult(PaymentResult result) {
        this.status = result.status();
        this.errorCode = result.errorCode();
        this.checkedAt = Instant.now();
        // 후속 조회가 실패해도 앞서 받은 PG 응답의 증거를 보존한다.
        if (result.pgStatus() != null) this.pgStatus = result.pgStatus();
        if (result.approvedAt() != null) this.approvedAt = result.approvedAt();
        if (result.pgAmount() != null) this.pgAmount = result.pgAmount();
        if (result.pgCurrency() != null) this.pgCurrency = result.pgCurrency();
    }

    public String getId() { return id; }
    public String getOrderId() { return orderId; }
    public String getAttemptId() { return attemptId; }
    public String getPaymentKey() { return paymentKey; }
    public long getRequestedAmount() { return requestedAmount; }
    public String getRequestedCurrency() { return requestedCurrency; }
    public PaymentStatus getStatus() { return status; }
    public Instant getApprovedAt() { return approvedAt; }
    public Instant getCheckedAt() { return checkedAt; }
    public String getPgStatus() { return pgStatus; }
    public String getErrorCode() { return errorCode; }
    public BigDecimal getPgAmount() { return pgAmount; }
    public String getPgCurrency() { return pgCurrency; }
}
