package com.example.payment.payment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** 실제로 돈이 나간 결제. 승인이 성공으로 확정된 시도마다 한 번 만들며, 주문당 최대 하나다. */
@Entity
@Table(name = "payments")
public class Payment {
    @Id
    @Column(length = 36)
    private String id;

    @Column(nullable = false, unique = true, length = 64)
    private String orderId;

    @Column(nullable = false, unique = true, length = 36)
    private String attemptId;

    @Column(nullable = false, unique = true, length = 200)
    private String paymentKey;

    @Column(nullable = false)
    private long amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false)
    private Instant approvedAt;

    @Column(nullable = false)
    private Instant createdAt;

    protected Payment() {}

    private Payment(PaymentAttempt attempt) {
        this.id = UUID.randomUUID().toString();
        this.orderId = attempt.getOrderId();
        this.attemptId = attempt.getId();
        this.paymentKey = attempt.getPaymentKey();
        // 성공 판정에서 토스 승인 금액이 시도 금액과 같음을 확인했다.
        this.amount = attempt.getAmount();
        this.currency = attempt.getCurrency();
        this.approvedAt = attempt.getPgApprovedAt();
        this.createdAt = Instant.now();
    }

    public static Payment approved(PaymentAttempt attempt) {
        if (attempt.getStatus() != PaymentAttemptStatus.SUCCEEDED) {
            throw new IllegalStateException("승인이 성공으로 확정된 시도만 결제로 기록할 수 있습니다.");
        }
        return new Payment(attempt);
    }

    public String getId() { return id; }
    public String getOrderId() { return orderId; }
    public String getAttemptId() { return attemptId; }
    public String getPaymentKey() { return paymentKey; }
    public long getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public Instant getApprovedAt() { return approvedAt; }
    public Instant getCreatedAt() { return createdAt; }
}
