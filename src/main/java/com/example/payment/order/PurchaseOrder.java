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

/** purchase_orders 테이블의 한 행이다. 주문번호, 총수량, 총금액과 주문 시각을 보관한다. */
@Entity
@Table(name = "purchase_orders")
public class PurchaseOrder {
    @Id
    @Column(length = 64)
    private String id;

    // 화면용 주문 요약명. 실제 구매 상품은 purchase_order_items에 있다.
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

    // 새 주문은 version이 null이어서 JPA가 INSERT하고, 이후 변경에는 낡은 버전의 덮어쓰기를 막는다.
    @Version
    private Long version;

    /** JPA가 DB에서 조회한 객체를 만들 때 사용하는 생성자다. */
    protected PurchaseOrder() {}

    /** 주문 요약과 서버에서 계산한 총수량·금액을 보관한다. */
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

    public void cancel() { status = OrderStatus.CANCELED; }
}
