package com.example.payment.order;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** purchase_orders 테이블의 한 행이다. 주문번호, 상품, 수량, 총금액과 주문 시각을 보관한다. */
@Entity
@Table(name = "purchase_orders")
public class PurchaseOrder {
    @Id
    @Column(length = 64)
    private String id;

    @Column(nullable = false, length = 100)
    private String productName;

    @Column(nullable = false)
    private int quantity;

    @Column(nullable = false)
    private long amount;

    @Column(nullable = false)
    private Instant createdAt;

    /** JPA가 DB에서 조회한 객체를 만들 때 사용하는 생성자다. */
    protected PurchaseOrder() {}

    /** 서비스에서 정한 상품·수량·총금액으로 새 주문을 만든다. DB 저장은 서비스에서 한다. */
    public PurchaseOrder(String productName, int quantity, long amount) {
        this.id = UUID.randomUUID().toString();
        this.productName = productName;
        this.quantity = quantity;
        this.amount = amount;
        this.createdAt = Instant.now();
    }

    public String getId() { return id; }
    public String getProductName() { return productName; }
    public int getQuantity() { return quantity; }
    public long getAmount() { return amount; }
    public Instant getCreatedAt() { return createdAt; }
}
