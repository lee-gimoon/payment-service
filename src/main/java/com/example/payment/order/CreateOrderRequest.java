package com.example.payment.order;

import java.util.List;

public record CreateOrderRequest(List<Item> items) {
    public record Item(String productId, String size, int quantity) {}
}
