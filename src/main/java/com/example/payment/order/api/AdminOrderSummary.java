package com.example.payment.order.api;

import com.example.payment.order.domain.PurchaseOrder;
import com.example.payment.order.domain.Shipment;
import java.time.Instant;

/** 관리자 주문 목록의 한 줄. 배송지는 받는 분 이름만 보여주고 전체 주소는 상세에서 본다. */
public record AdminOrderSummary(String orderId, String productName, int quantity, long amount, Instant paidAt,
                                String recipientName, DeliveryResponse delivery) {
    public static AdminOrderSummary of(PurchaseOrder order, Shipment shipment) {
        return new AdminOrderSummary(order.getId(), order.getProductName(), order.getQuantity(), order.getAmount(),
                order.getPaidAt(), order.getShipping() == null ? null : order.getShipping().recipientName(),
                DeliveryResponse.of(order, shipment));
    }
}
