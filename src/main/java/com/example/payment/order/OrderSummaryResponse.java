package com.example.payment.order;

import java.time.Instant;

/**
 * 마이페이지 주문 목록의 한 줄. 결제 시도와 상품별 내역은 주문 조회에서 본다.
 *
 * @param delivery 배송 단계. 결제 완료 전 주문은 null이다.
 */
public record OrderSummaryResponse(String orderId, String productName, int quantity, long amount, String currency,
                                   Instant createdAt, OrderStatus status, DeliveryStatus delivery) {
    static OrderSummaryResponse of(PurchaseOrder order, Shipment shipment) {
        DeliveryResponse delivery = DeliveryResponse.of(order, shipment);
        return new OrderSummaryResponse(order.getId(), order.getProductName(), order.getQuantity(),
                order.getAmount(), order.getCurrency(), order.getCreatedAt(), order.getStatus(),
                delivery == null ? null : delivery.status());
    }
}
