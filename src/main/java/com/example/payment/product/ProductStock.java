package com.example.payment.product;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;

/** 상품 사이즈별 남은 수량. 수량은 조건부 UPDATE로만 바꿔 동시에 주문해도 음수가 되지 않게 한다. */
@Entity
@Table(name = "product_stocks")
@IdClass(ProductStock.Key.class)
public class ProductStock {
    @Id
    @Column(name = "product_id", length = 32)
    private String productId;

    @Id
    @Column(length = 4)
    private String size;

    @Column(nullable = false)
    private int quantity;

    protected ProductStock() {}

    public String getProductId() { return productId; }
    public String getSize() { return size; }
    public int getQuantity() { return quantity; }

    public record Key(String productId, String size) implements Serializable {}
}
