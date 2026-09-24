package com.example.payment.order;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import com.example.payment.product.Product;
import java.util.Set;
import java.util.UUID;

/** purchase_order_items 테이블의 한 행. 구입 당시 상품명·단가·옵션을 보관한다. */
@Entity
@Table(name = "purchase_order_items")
public class OrderItem {
    @Id
    @Column(length = 36)
    private String id;

    // 여러 주문 항목이 한 주문에 속한다. order_id가 purchase_orders.id를 참조한다.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private PurchaseOrder order;

    @Column(name = "line_number", nullable = false)
    private int lineNumber;

    // 현재 상품을 가리키되, 이름과 단가는 아래 필드에 구입 당시 값으로 따로 보존한다.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "product_name", nullable = false, length = 100)
    private String productName;

    @Column(name = "size", nullable = false, length = 4)
    private String size;

    @Column(name = "unit_price", nullable = false)
    private long unitPrice;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    protected OrderItem() {}

    public OrderItem(Product product, String size, int quantity) {
        if (product == null || !product.isActive() || product.getPrice() <= 0
                || size == null || !Set.of("S", "M", "L", "XL").contains(size)
                || quantity < 1 || quantity > 10) {
            throw new IllegalArgumentException("판매 중인 상품과 유효한 옵션·수량이 필요합니다.");
        }
        this.id = UUID.randomUUID().toString();
        this.product = product;
        this.productName = product.getName();
        this.size = size;
        this.unitPrice = product.getPrice();
        this.quantity = quantity;
    }

    /** 주문에 추가할 때 부모와 목록 순서를 연결한다. */
    void attachTo(PurchaseOrder order, int lineNumber) {
        this.order = order;
        this.lineNumber = lineNumber;
    }

    public String getId() { return id; }
    public String getProductId() { return product.getId(); }
    public String getProductName() { return productName; }
    public String getSize() { return size; }
    public long getUnitPrice() { return unitPrice; }
    public int getQuantity() { return quantity; }
}
