package com.example.payment.product;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** products 테이블의 상품 한 행이다. 현재 판매 가격은 주문 생성 시 서버가 여기서 읽는다. */
@Entity
@Table(name = "products")
public class Product {
    @Id
    @Column(length = 32)
    private String id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 100)
    private String subtitle;

    @Column(nullable = false, length = 40)
    private String category;

    @Column(nullable = false)
    private long price;

    @Column(nullable = false, length = 7)
    private String color;

    @Column(nullable = false, length = 7)
    private String stage;

    @Column(nullable = false, length = 32)
    private String artwork;

    @Column(nullable = false, length = 32)
    private String badge;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    // 판매 중지해도 과거 주문이 참조할 수 있도록 상품 행은 남겨 둔다.
    @Column(nullable = false)
    private boolean active;

    /** JPA가 DB의 상품 행을 객체로 만들 때 사용한다. */
    protected Product() {}

    public Product(String id, String name, String subtitle, String category, long price,
                   String color, String stage, String artwork, String badge, int displayOrder) {
        this.id = id;
        this.name = name;
        this.subtitle = subtitle;
        this.category = category;
        this.price = price;
        this.color = color;
        this.stage = stage;
        this.artwork = artwork;
        this.badge = badge;
        this.displayOrder = displayOrder;
        this.active = true;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getSubtitle() { return subtitle; }
    public String getCategory() { return category; }
    public long getPrice() { return price; }
    public String getColor() { return color; }
    public String getStage() { return stage; }
    public String getArtwork() { return artwork; }
    public String getBadge() { return badge; }
    public int getDisplayOrder() { return displayOrder; }
    public boolean isActive() { return active; }
}
