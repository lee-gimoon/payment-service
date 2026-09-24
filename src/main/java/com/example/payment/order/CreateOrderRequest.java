package com.example.payment.order;

import java.util.List;

/** 클라이언트는 상품 식별자와 옵션만 보낸다. 가격은 DB의 Product에서 결정한다. */
public record CreateOrderRequest(List<Item> items) {
    public record Item(String productId, String size, int quantity) {}
}
