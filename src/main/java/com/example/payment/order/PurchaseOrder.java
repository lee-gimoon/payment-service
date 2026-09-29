package com.example.payment.order;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/** 주문 금액과 통화는 생성 이후 변경하지 않는다. */
@Entity
@Table(name = "purchase_orders")
public class PurchaseOrder {
    @Id
    @Column(length = 64)
    private String id;

    // 주문한 회원의 Keycloak ID(access token의 sub). 로그인 도입 전에 만든 주문은 비어 있다.
    @Column(length = 64)
    private String customerId;

    // 결제창에 표시할 요약명. 상품별 구매 내역은 주문 항목에 보존한다.
    @Column(nullable = false, length = 100)
    private String productName;

    @Column(nullable = false)
    private int quantity;

    @Column(nullable = false)
    private long amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false)
    private Instant createdAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private OrderStatus status;

    // 승인 슬롯. 진행 중이거나 성공한 승인의 시도 ID이며, 주문당 하나만 둘 수 있다.
    @Column(length = 36)
    private String approvalAttemptId;

    private Instant paidAt;

    @Version
    private Long version;

    protected PurchaseOrder() {}

    PurchaseOrder(String customerId, String firstProductName, int productCount, int quantity, long amount) {
        if (customerId == null || customerId.isBlank()) {
            throw new IllegalArgumentException("주문한 회원을 확인해주세요.");
        }
        if (firstProductName == null || firstProductName.isBlank() || productCount < 1
                || quantity < 1 || quantity > 100 || productCount > quantity || amount <= 0) {
            throw new IllegalArgumentException("주문 수량과 금액을 확인해주세요.");
        }
        this.id = UUID.randomUUID().toString();
        this.customerId = customerId;
        this.createdAt = Instant.now();
        this.status = OrderStatus.PENDING_PAYMENT;
        this.quantity = quantity;
        this.amount = amount;
        this.currency = "KRW";
        String suffix = productCount > 1 ? " 외 " + (productCount - 1) + "종" : "";
        this.productName = firstProductName.substring(0,
                Math.min(firstProductName.length(), 100 - suffix.length())) + suffix;
    }

    public String getId() { return id; }
    public String getCustomerId() { return customerId; }
    public String getProductName() { return productName; }
    public int getQuantity() { return quantity; }
    public long getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public Instant getCreatedAt() { return createdAt; }
    public OrderStatus getStatus() { return status; }
    public String getApprovalAttemptId() { return approvalAttemptId; }
    public Instant getPaidAt() { return paidAt; }

    /** 주인이 없는 이전 주문은 누구의 것도 아니다. */
    public boolean isOwnedBy(String customerId) {
        return this.customerId != null && this.customerId.equals(customerId);
    }

    /** 진행 중이거나 성공한 승인이 없을 때만 새 결제 시도와 승인을 받는다. */
    public boolean acceptsNewPayment() {
        return status == OrderStatus.PENDING_PAYMENT;
    }

    public void claimApproval(String attemptId) {
        if (!acceptsNewPayment()) throw new IllegalStateException("이 주문은 다른 승인이 진행 중이거나 완료되었습니다.");
        status = OrderStatus.PAYMENT_IN_PROGRESS;
        approvalAttemptId = attemptId;
    }

    /** 슬롯을 잡은 시도의 실패가 PG에서 확인된 뒤에만 슬롯을 돌려준다. */
    public void releaseApproval(String attemptId) {
        requireSlotHolder(attemptId);
        status = OrderStatus.PENDING_PAYMENT;
        approvalAttemptId = null;
    }

    public void markPaid(String attemptId) {
        if (status == OrderStatus.PAID && attemptId.equals(approvalAttemptId)) return;
        requireSlotHolder(attemptId);
        status = OrderStatus.PAID;
        paidAt = Instant.now();
    }

    private void requireSlotHolder(String attemptId) {
        if (status != OrderStatus.PAYMENT_IN_PROGRESS || !attemptId.equals(approvalAttemptId)) {
            throw new IllegalStateException("주문의 승인 슬롯을 가진 결제 시도가 아닙니다.");
        }
    }
}
