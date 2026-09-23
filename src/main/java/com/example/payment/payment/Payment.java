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
     * 결제 행을 갱신할 때 충돌을 감지하고, 새 결제를 INSERT 대상으로 판별하게 하는 버전 값이다.
     * Spring Data JPA는 {@code Long version}이 null이면 새 엔티티로 보고 {@code persist()}를 호출한다.
     * 버전 필드가 없으면 ID로 새 엔티티인지 판단한다. 이 엔티티는 생성 시 {@code orderId}를 이미
     * 지정하므로, 새 객체인데도 ID가 있다는 이유로 {@code merge()}를 호출한다.
     * 동시 요청이 모두 결제 기록을 찾지 못한 뒤 저장할 때, 뒤의 merge()가 먼저 생성된 행을 갱신하면
     * 두 요청 모두 토스 승인 호출까지 진행할 위험이 있다. persist()를 쓰면 두 번째 INSERT가
     * 주문번호 기본 키 제약에 걸려 토스 호출 전에 중단된다.
     * 기존 행을 갱신할 때는 DB의 버전과 비교해 오래된 결과가 최신 결과를 덮어쓰지 못하게 한다.
     */
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
