/* 파일 역할: 주문에 연결된 결제 한 건의 저장 구조와 상태 변경·재확인 규칙을 정의한다. */
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

/**
 * payments 테이블의 JPA 엔티티다. 주문당 하나의 결제 시도와 그 시도에 속한 최신 작업을 관리한다.
 * attemptId는 결제 시도 동안 유지하고, operationId는 승인·재확인 작업을 시작할 때마다 갱신한다.
 */
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

    /** JPA가 DB의 결제 기록을 엔티티로 복원할 때 사용하는 기본 생성자다. */
    protected Payment() {}

    /** 새 결제 시도의 고정 식별자를 발급하고, 첫 승인 작업을 PROCESSING으로 시작한다. 저장은 호출자가 한다. */
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

    /** 승인 또는 재확인 작업의 식별자와 처리 기한을 갱신하고 상태를 PROCESSING으로 바꾼다. */
    public void beginOperation(Instant now, Duration lease) {
        this.operationId = UUID.randomUUID().toString();
        this.status = PaymentStatus.PROCESSING;
        this.processingUntil = now.plus(lease);
    }

    /**
     * 응답에 표시할 상태를 계산한다. 처리 기한이 지난 PROCESSING은 UNKNOWN으로 보여준다.
     * 이 메서드는 status 필드나 DB를 수정하지 않으므로 저장 상태와 응답 상태가 다를 수 있다.
     */
    public PaymentStatus visibleStatus(Instant now) {
        return status == PaymentStatus.PROCESSING && !now.isBefore(processingUntil)
                ? PaymentStatus.UNKNOWN : status;
    }

    /** 현재 표시 상태가 UNKNOWN일 때만 PG 결과 재확인을 허용한다. */
    public boolean canReconcile(Instant now) {
        return visibleStatus(now) == PaymentStatus.UNKNOWN;
    }

    /** 현재 진행 중인 작업과 식별자가 일치할 때만 PG 결과를 반영하여 이전 작업의 늦은 응답을 무시한다. */
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

    /** 결제가 연결된 주문번호이자 payments 테이블의 기본 키를 반환한다. */
    public String getOrderId() { return orderId; }
    /** PG 승인 요청의 Idempotency-Key로 사용하는 고정 결제 시도 식별자를 반환한다. */
    public String getAttemptId() { return attemptId; }
    /** PG 승인·조회에 사용하는 토스 결제 키를 반환한다. */
    public String getPaymentKey() { return paymentKey; }
    /** 주문 금액과 일치해야 하는 결제 금액을 반환한다. */
    public long getAmount() { return amount; }
    /** 결과를 반영할 권한이 있는 최신 승인·조회 작업의 식별자를 반환한다. */
    public String getOperationId() { return operationId; }
    /** PG 결과를 엔티티에 마지막으로 반영한 시각을 반환한다. */
    public Instant getCheckedAt() { return checkedAt; }
    /** 승인 성공 결과에서 확인한 실제 PG 승인 시각을 반환한다. */
    public Instant getApprovedAt() { return approvedAt; }
    /** DONE 등 PG 응답에서 확인한 원본 상태를 반환한다. */
    public String getPgStatus() { return pgStatus; }
    /** 결제 실패 또는 결과 미확정의 원인을 설명하는 코드를 반환한다. */
    public String getErrorCode() { return errorCode; }
}
