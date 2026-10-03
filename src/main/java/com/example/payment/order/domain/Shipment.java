package com.example.payment.order.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/** 결제 완료 주문의 배송. 관리자가 송장을 등록하면 생기고, 배송 완료로 끝난다. 지금은 주문당 한 건이다. */
@Entity
@Table(name = "shipments")
public class Shipment {
    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "order_id", nullable = false, length = 64, unique = true)
    private String orderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ShipmentStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Carrier carrier;

    @Column(name = "tracking_number", nullable = false, length = 20)
    private String trackingNumber;

    @Column(name = "shipped_at", nullable = false)
    private Instant shippedAt;

    private Instant deliveredAt;

    @Version
    private Long version;

    protected Shipment() {}

    public Shipment(String orderId, Carrier carrier, String trackingNumber) {
        this.id = UUID.randomUUID().toString();
        this.orderId = orderId;
        this.status = ShipmentStatus.SHIPPED;
        this.carrier = carrier;
        this.trackingNumber = trackingNumber;
        this.shippedAt = Instant.now();
    }

    /** 배송 중에는 잘못 넣은 택배사·송장번호를 고칠 수 있다. 출고 시각은 처음 등록한 때로 둔다. */
    public void correct(Carrier carrier, String trackingNumber) {
        if (status == ShipmentStatus.DELIVERED) {
            throw new IllegalStateException("배송 완료된 송장은 고칠 수 없습니다.");
        }
        this.carrier = carrier;
        this.trackingNumber = trackingNumber;
    }

    /** 이미 배송 완료면 그대로 둔다. */
    public void markDelivered() {
        if (status == ShipmentStatus.DELIVERED) return;
        status = ShipmentStatus.DELIVERED;
        deliveredAt = Instant.now();
    }

    public String getOrderId() { return orderId; }
    public ShipmentStatus getStatus() { return status; }
    public Carrier getCarrier() { return carrier; }
    public String getTrackingNumber() { return trackingNumber; }
    public Instant getShippedAt() { return shippedAt; }
    public Instant getDeliveredAt() { return deliveredAt; }
}
