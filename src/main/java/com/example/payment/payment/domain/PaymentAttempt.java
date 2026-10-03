package com.example.payment.payment.domain;

import com.example.payment.order.domain.PurchaseOrder;
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

/** 결제창 인증부터 PG 승인 확정까지 한 번의 결제 시도. 승인은 시도마다 최대 한 번이다. */
@Entity
@Table(name = "payment_attempts")
public class PaymentAttempt {
    @Id
    @Column(length = 36)
    private String id;

    @Column(nullable = false, length = 64)
    private String orderId;

    // 주문에서 복사한 서버 금액. 브라우저가 보낸 금액은 비교에만 쓴다.
    @Column(nullable = false)
    private long amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private PaymentAttemptStatus status;

    @Column(unique = true, length = 200)
    private String paymentKey;

    @Column(nullable = false)
    private Instant startedAt;

    private Instant approvalRequestedAt;
    private Instant lastCheckedAt;
    private Instant finishedAt;

    @Column(length = 40)
    private String pgStatus;

    private Instant pgApprovedAt;

    @Column(precision = 24, scale = 6)
    private BigDecimal pgAmount;

    @Column(length = 3)
    private String pgCurrency;

    @Column(length = 80)
    private String errorCode;

    @Version
    private Long version;

    protected PaymentAttempt() {}

    private PaymentAttempt(PurchaseOrder order) {
        this.id = UUID.randomUUID().toString();
        this.orderId = order.getId();
        this.amount = order.getAmount();
        this.currency = order.getCurrency();
        this.status = PaymentAttemptStatus.STARTED;
        this.startedAt = Instant.now();
    }

    public static PaymentAttempt start(PurchaseOrder order) {
        return new PaymentAttempt(order);
    }

    public void authenticationCanceled(String code) {
        finishAuthentication(PaymentAttemptStatus.AUTH_CANCELED, code);
    }

    public void authenticationFailed(String code) {
        finishAuthentication(PaymentAttemptStatus.AUTH_FAILED, code);
    }

    private void finishAuthentication(PaymentAttemptStatus outcome, String code) {
        // 늦게 도착한 브라우저 이벤트가 서버 승인 상태를 덮어쓰지 못하게 한다.
        if (status != PaymentAttemptStatus.STARTED) return;
        status = outcome;
        errorCode = code;
        finishedAt = Instant.now();
    }

    /** 승인 관문을 통과한 시도에 결제 키를 한 번만 연결한다. 호출 전에 주문의 승인 슬롯을 잡아야 한다. */
    public void requestApproval(String paymentKey) {
        if (status != PaymentAttemptStatus.STARTED) {
            throw new IllegalStateException("인증 대기 중인 결제 시도만 승인을 요청할 수 있습니다.");
        }
        if (paymentKey == null || paymentKey.isBlank()) {
            throw new IllegalArgumentException("결제 키가 필요합니다.");
        }
        this.paymentKey = paymentKey;
        this.status = PaymentAttemptStatus.APPROVING;
        this.approvalRequestedAt = Instant.now();
    }

    public void succeed(PaymentResult evidence) {
        requireAwaitingResult();
        if (!matchesApproval(evidence)) {
            throw new IllegalArgumentException("승인 증거가 저장된 결제 정보와 다릅니다.");
        }
        record(evidence);
        status = PaymentAttemptStatus.SUCCEEDED;
        finishedAt = Instant.now();
    }

    /** PG가 거절·만료를 확인한 경우에만 호출한다. 결과를 모르는 것은 실패가 아니다. */
    public void fail(PaymentResult evidence) {
        requireAwaitingResult();
        record(evidence);
        status = PaymentAttemptStatus.FAILED;
        finishedAt = Instant.now();
    }

    public void markUnknown(PaymentResult evidence) {
        requireAwaitingResult();
        record(evidence);
        // 수동 확인으로 넘어간 시도는 다시 자동 확인 대상으로 되돌리지 않는다.
        if (status == PaymentAttemptStatus.APPROVING) status = PaymentAttemptStatus.UNKNOWN;
    }

    public void requireReview(PaymentResult evidence) {
        requireAwaitingResult();
        record(evidence);
        status = PaymentAttemptStatus.REVIEW_REQUIRED;
    }

    private void requireAwaitingResult() {
        if (!isAwaitingResult()) {
            throw new IllegalStateException("승인 결과를 기다리는 결제 시도가 아닙니다: " + status);
        }
    }

    private void record(PaymentResult evidence) {
        errorCode = evidence.errorCode();
        lastCheckedAt = Instant.now();
        // 후속 조회가 실패해도 앞서 받은 PG 응답의 증거를 보존한다.
        if (evidence.pgStatus() != null) pgStatus = evidence.pgStatus();
        if (evidence.approvedAt() != null) pgApprovedAt = evidence.approvedAt();
        if (evidence.pgAmount() != null) pgAmount = evidence.pgAmount();
        if (evidence.pgCurrency() != null) pgCurrency = evidence.pgCurrency();
    }

    public boolean matchesApproval(PaymentResult evidence) {
        return evidence.outcome() == PaymentResult.Outcome.SUCCEEDED
                && "DONE".equals(evidence.pgStatus()) && evidence.approvedAt() != null
                && evidence.pgAmount() != null && evidence.pgAmount().compareTo(BigDecimal.valueOf(amount)) == 0
                && currency.equals(evidence.pgCurrency());
    }

    public boolean isAwaitingResult() {
        return PaymentAttemptStatus.AWAITING_RESULT.contains(status);
    }

    /** 자동 복구 대상인지 판단한다. 마지막 확인 이후 충분히 시간이 지난 미확정 시도만 조회한다. */
    public boolean needsRecovery(Instant checkedBefore) {
        Instant lastActivity = lastCheckedAt != null ? lastCheckedAt : approvalRequestedAt;
        return PaymentAttemptStatus.RECOVERABLE.contains(status) && lastActivity.isBefore(checkedBefore);
    }

    public ApprovalRequest approvalRequest() {
        if (paymentKey == null) throw new IllegalStateException("승인을 요청하지 않은 결제 시도입니다.");
        return new ApprovalRequest(id, orderId, paymentKey, amount, currency);
    }

    public String getId() { return id; }
    public String getOrderId() { return orderId; }
    public long getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public PaymentAttemptStatus getStatus() { return status; }
    public String getPaymentKey() { return paymentKey; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getApprovalRequestedAt() { return approvalRequestedAt; }
    public Instant getLastCheckedAt() { return lastCheckedAt; }
    public Instant getFinishedAt() { return finishedAt; }
    public String getPgStatus() { return pgStatus; }
    public Instant getPgApprovedAt() { return pgApprovedAt; }
    public BigDecimal getPgAmount() { return pgAmount; }
    public String getPgCurrency() { return pgCurrency; }
    public String getErrorCode() { return errorCode; }
}
