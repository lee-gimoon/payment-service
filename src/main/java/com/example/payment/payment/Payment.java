package com.example.payment.payment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;

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
        this.pgStatus = result.pgStatus();
        this.errorCode = result.errorCode();
        this.approvedAt = result.approvedAt();
        this.checkedAt = Instant.now();
    }

    /** 결과가 아직 확정되지 않았다면 프론트에서 PG 결과 재확인 버튼을 표시한다. */
    public boolean canReconcile() {
        return status == PaymentStatus.PROCESSING || status == PaymentStatus.UNKNOWN;
    }

    public String getOrderId() { return orderId; }
    public String getPaymentKey() { return paymentKey; }
    public PaymentStatus getStatus() { return status; }
    public Instant getApprovedAt() { return approvedAt; }
    public Instant getCheckedAt() { return checkedAt; }
    public String getPgStatus() { return pgStatus; }
    public String getErrorCode() { return errorCode; }
}
