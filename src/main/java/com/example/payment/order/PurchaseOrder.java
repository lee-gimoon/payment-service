package com.example.payment.order;

import jakarta.persistence.Column;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** purchase_orders 테이블의 한 행이다. 주문번호, 총수량, 총금액과 주문 시각을 보관한다. */
@Entity
@Table(name = "purchase_orders")
public class PurchaseOrder {
    @Id
    @Column(length = 64)
    private String id;

    // 화면용 주문 요약명. 실제 구매 상품은 items에 있다.
    @Column(nullable = false, length = 100)
    private String productName;

    @Column(nullable = false)
    private int quantity;

    @Column(nullable = false)
    private long amount;

    @Column(nullable = false)
    private Instant createdAt;

    // 새 주문은 version이 null이어서 JPA가 INSERT하고, 이후 변경에는 낡은 버전의 덮어쓰기를 막는다.
    @Version
    private Long version;

    // OrderItem.order의 order_id로 연결한다. 새 주문과 함께 저장하고, 조회할 때는 필요 시 불러온다.
    @OneToMany(mappedBy = "order", cascade = CascadeType.PERSIST, fetch = FetchType.LAZY)
    @OrderBy("lineNumber ASC")
    private List<OrderItem> items = new ArrayList<>();

    /** JPA가 DB에서 조회한 객체를 만들 때 사용하는 생성자다. */
    protected PurchaseOrder() {}

    /** 새 주문의 수량·금액은 항목 스냅샷에서 직접 계산해 서로 어긋나지 않게 한다. */
    public PurchaseOrder(List<OrderItem> items) {
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("주문 항목이 필요합니다.");
        }
        this.id = UUID.randomUUID().toString();
        this.createdAt = Instant.now();
        Set<String> productIds = new HashSet<>();
        long totalAmount = 0;
        for (OrderItem item : items) {
            this.quantity = Math.addExact(this.quantity, item.getQuantity());
            totalAmount = Math.addExact(totalAmount, Math.multiplyExact(item.getUnitPrice(), item.getQuantity()));
            productIds.add(item.getProductId());
            item.attachTo(this, this.items.size());
            this.items.add(item);
        }
        if (this.quantity > 100 || totalAmount <= 0) {
            throw new IllegalArgumentException("주문 수량과 금액을 확인해주세요.");
        }
        this.amount = totalAmount;
        String firstName = this.items.getFirst().getProductName();
        String suffix = productIds.size() > 1 ? " 외 " + (productIds.size() - 1) + "종" : "";
        this.productName = firstName.substring(0, Math.min(firstName.length(), 100 - suffix.length())) + suffix;
    }

    public String getId() { return id; }
    public String getProductName() { return productName; }
    public int getQuantity() { return quantity; }
    public long getAmount() { return amount; }
    public Instant getCreatedAt() { return createdAt; }
    public List<OrderItem> getItems() { return List.copyOf(items); }
}
