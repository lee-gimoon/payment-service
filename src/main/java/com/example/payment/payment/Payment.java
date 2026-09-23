package com.example.payment.payment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.math.BigDecimal;
import java.util.UUID;

/** payments 테이블의 한 행이다. 주문번호, 토스 결제 키와 승인 결과를 보관한다. */
@Entity
@Table(name = "payments")
public class Payment {
    /** 주문당 결제 한 건만 저장한다. 주문번호가 이 테이블의 기본 키이기도 하다. */
    @Id
    @Column(length = 64)
    private String orderId;

    @Column(nullable = false, unique = true, length = 200)
    private String paymentKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status;

    private Instant approvedAt;
    private Instant checkedAt;
    private Instant canceledAt;

    @Column(precision = 24, scale = 6)
    private BigDecimal pgAmount;
    @Column(length = 3)
    private String pgCurrency;

    /** 취소 전에 저장한다. 결과가 불명확할 때 취소 요청을 추적하는 고정 멱등키다. */
    @Column(length = 36, unique = true)
    private String cancelIdempotencyKey;
    private Instant cancelRequestedAt;

    @Column(length = 40)
    private String pgStatus;
    @Column(length = 80)
    private String errorCode;

    /** JPA가 저장할 때 검사하는 번호다. 늦은 응답이 다른 요청의 저장 결과를 덮어쓰지 못하게 한다. */
    @Version
    private Long version;

    protected Payment() {}

    /** 결제수단 인증 후 받은 paymentKey로 승인 요청 정보를 만든다. 저장은 서비스에서 한다. */
    public Payment(String orderId, String paymentKey) {
        this.orderId = orderId;
        this.paymentKey = paymentKey;
        this.status = PaymentStatus.PROCESSING;
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

    public String getOrderId() { return orderId; }
    public String getPaymentKey() { return paymentKey; }
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
