package com.example.payment.order.api;

import java.util.List;

/**
 * @param addressId 마이페이지에 저장한 내 배송지 ID. 서버가 주소를 주문에 복사한다.
 * @param deliveryMemo 배송 메모. 선택이며 최대 50자다.
 */
public record CreateOrderRequest(List<Item> items, String addressId, String deliveryMemo) {
    public record Item(String productId, String size, int quantity) {}
}
