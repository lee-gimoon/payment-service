package com.example.payment.payment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** 인증을 마친 한 번의 결제 거래다. 실패 기록을 보존하고 같은 주문의 재시도를 허용한다. */
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
    private Instant canceledAt;

    @Column(precision = 24, scale = 6)
    private BigDecimal pgAmount;
    @Column(length = 3)
    private String pgCurrency;

    /**
     * 취소 요청 전에 "이 결제를 취소하려고 한다"는 의도로 저장하는 UUID 멱등키다.
     * 같은 요청을 다시 보내더라도 토스에서 중복 취소로 처리하지 않도록 Idempotency-Key 헤더에 같은 값을 보낸다.
     */
    @Column(length = 36, unique = true)
    private String cancelIdempotencyKey;

    /** 취소 요청 전에 "이 결제를 취소하려고 한다"는 의도를 DB에 저장한 시각이다. 실제 취소 완료 시각은 canceledAt이다. */
    private Instant cancelRequestedAt;

    @Column(length = 40)
    private String pgStatus;
    @Column(length = 80)
    private String errorCode;

    /**
     * 새 거래는 null 버전으로 INSERT하고 기존 거래는 낡은 버전의 결과 덮어쓰기를 막는다.
     * 주문당 진행·완료 거래 한 건 제약은 DB의 부분 고유 인덱스로 검사한다.
     */
    @Version
    private Long version;

    protected Payment() {}

    /** 결제수단 인증 후 받은 paymentKey로 승인 요청 정보를 만든다. 저장은 서비스에서 한다. */
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

    /** 토스에서 확인한 승인 결과를 기록한다. */
    public void applyResult(PaymentResult result) {
        this.status = result.status();
        if (result.pgStatus() != null) this.pgStatus = result.pgStatus();
        this.errorCode = result.errorCode();
        if (result.approvedAt() != null) this.approvedAt = result.approvedAt();
        if (result.pgAmount() != null) this.pgAmount = result.pgAmount();
        if (result.pgCurrency() != null) this.pgCurrency = result.pgCurrency();
        if (result.canceledAt() != null) this.canceledAt = result.canceledAt();
        this.checkedAt = Instant.now();
        if (status == PaymentStatus.CANCEL_PENDING && cancelIdempotencyKey == null) {
            cancelIdempotencyKey = UUID.randomUUID().toString();
            cancelRequestedAt = checkedAt;
        }
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
    public Instant getCanceledAt() { return canceledAt; }
    public String getCancelIdempotencyKey() { return cancelIdempotencyKey; }
    public Instant getCancelRequestedAt() { return cancelRequestedAt; }
}
