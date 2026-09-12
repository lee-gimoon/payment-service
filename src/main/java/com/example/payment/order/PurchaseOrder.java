/* 파일 역할: 주문 한 건의 저장 구조와 학습용 고정 상품 주문을 만드는 규칙을 담는다. */
package com.example.payment.order;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** purchase_orders 테이블의 한 행에 대응하는 JPA 엔티티로 주문번호·상품·금액·생성 시각을 보관한다. */
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
    @Column(nullable = false, length = 3)
    private String currency;
    @Column(nullable = false)
    private Instant createdAt;

    /** JPA가 DB에서 읽은 값으로 엔티티를 복원할 때 사용하는 기본 생성자다. */
    protected PurchaseOrder() {}

    /** 새 주문번호를 발급하고 티셔츠 1장·10,000원·KRW로 주문을 만든다. DB 저장은 서비스가 담당한다. */
    public static PurchaseOrder tShirt(Instant now) {
        PurchaseOrder order = new PurchaseOrder();
        order.id = UUID.randomUUID().toString();
        order.productName = "티셔츠";
        order.quantity = 1;
        order.amount = 10_000L;
        order.currency = "KRW";
        order.createdAt = now;
        return order;
    }

    /** API 조회와 결제 연결에 사용하는 주문번호를 반환한다. */
    public String getId() { return id; }
    /** 주문 응답에 표시할 상품명을 반환한다. */
    public String getProductName() { return productName; }
    /** 서버가 정한 주문 수량을 반환한다. */
    public int getQuantity() { return quantity; }
    /** 승인 요청 금액을 검증할 기준인 주문 금액을 원 단위로 반환한다. */
    public long getAmount() { return amount; }
    /** 주문 금액의 통화 코드인 KRW를 반환한다. */
    public String getCurrency() { return currency; }
    /** 주문을 생성한 시각을 반환한다. */
    public Instant getCreatedAt() { return createdAt; }
}
