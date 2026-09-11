package com.example.payment.order;

import com.example.payment.api.ApiException;
import jakarta.validation.constraints.Pattern;
import java.net.URI;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OrderController {
    private final OrderService orders;

    public OrderController(OrderService orders) { this.orders = orders; }

    @PostMapping("/orders")
    ResponseEntity<OrderResponse> create(@RequestBody(required = false) Map<String, Object> request) {
        if (request != null && !request.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "주문 상품, 수량과 가격은 서버에서 정합니다. 빈 본문으로 요청해주세요.");
        }
        OrderResponse order = orders.create();
        return ResponseEntity.created(URI.create("/orders/" + order.orderId())).body(order);
    }

    @GetMapping("/orders/{orderId}")
    OrderResponse get(@PathVariable @Pattern(regexp = "[a-zA-Z0-9_-]{6,64}") String orderId) {
        return orders.get(orderId);
    }
}
