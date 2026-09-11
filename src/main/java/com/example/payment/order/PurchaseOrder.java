package com.example.payment.order;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

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

    protected PurchaseOrder() {}

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

    public String getId() { return id; }
    public String getProductName() { return productName; }
    public int getQuantity() { return quantity; }
    public long getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public Instant getCreatedAt() { return createdAt; }
}
