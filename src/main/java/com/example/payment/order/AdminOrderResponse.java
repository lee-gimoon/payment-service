package com.example.payment.order;

import java.time.Instant;
import java.util.List;

/** 관리자 주문 상세. 포장·발송에 필요한 상품 내역과 주문에 복사된 배송지를 담는다. */
public record AdminOrderResponse(String orderId, String productName, int quantity, long amount, String currency,
                                 List<OrderResponse.ItemResponse> items, ShippingAddress shipping,
                                 Instant createdAt, Instant paidAt, OrderStatus status, DeliveryResponse delivery) {
    static AdminOrderResponse of(PurchaseOrder order, List<OrderItem> items, Shipment shipment) {
        return new AdminOrderResponse(order.getId(), order.getProductName(), order.getQuantity(), order.getAmount(),
                order.getCurrency(), items.stream().map(OrderResponse.ItemResponse::of).toList(), order.getShipping(),
                order.getCreatedAt(), order.getPaidAt(), order.getStatus(), DeliveryResponse.of(order, shipment));
    }
}
