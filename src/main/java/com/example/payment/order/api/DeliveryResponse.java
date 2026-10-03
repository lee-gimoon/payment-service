package com.example.payment.order.api;

import com.example.payment.order.domain.Carrier;
import com.example.payment.order.domain.DeliveryStatus;
import com.example.payment.order.domain.OrderStatus;
import com.example.payment.order.domain.PurchaseOrder;
import com.example.payment.order.domain.Shipment;
import java.time.Instant;

/** 주문의 배송 단계. 송장 정보는 송장을 등록한 뒤에만 있다. */
public record DeliveryResponse(DeliveryStatus status, Carrier carrier, String trackingNumber,
                               Instant shippedAt, Instant deliveredAt) {
    /** 결제 완료 전 주문은 배송 단계가 없어 null이다. 결제 완료 뒤 송장이 없으면 상품 준비 중이다. */
    static DeliveryResponse of(PurchaseOrder order, Shipment shipment) {
        if (order.getStatus() != OrderStatus.PAID) return null;
        if (shipment == null) return new DeliveryResponse(DeliveryStatus.PREPARING, null, null, null, null);
        return new DeliveryResponse(DeliveryStatus.valueOf(shipment.getStatus().name()), shipment.getCarrier(),
                shipment.getTrackingNumber(), shipment.getShippedAt(), shipment.getDeliveredAt());
    }
}
