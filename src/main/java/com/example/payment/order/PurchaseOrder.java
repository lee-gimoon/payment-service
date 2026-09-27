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

    @Version
    private Long version;

    protected PurchaseOrder() {}

    PurchaseOrder(String firstProductName, int productCount, int quantity, long amount) {
        if (firstProductName == null || firstProductName.isBlank() || productCount < 1
                || quantity < 1 || quantity > 100 || productCount > quantity || amount <= 0) {
            throw new IllegalArgumentException("주문 수량과 금액을 확인해주세요.");
        }
        this.id = UUID.randomUUID().toString();
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
    public String getProductName() { return productName; }
    public int getQuantity() { return quantity; }
    public long getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public Instant getCreatedAt() { return createdAt; }
    public OrderStatus getStatus() { return status; }

    public void confirm() {
        if (status != OrderStatus.PENDING_PAYMENT) throw new IllegalStateException("결제 대기 주문만 확정할 수 있습니다.");
        status = OrderStatus.CONFIRMED;
    }
}
