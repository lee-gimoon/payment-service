package com.example.payment.order;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** 브라우저의 주문 HTTP 요청을 OrderService에 전달한다. */
@RestController
@Tag(name = "Orders")
public class OrderController {
    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    /** 장바구니의 상품·옵션만 받고 가격은 서버 상품 목록에서 결정한다. */
    @PostMapping("/orders")
    @Operation(summary = "주문 생성")
    public ResponseEntity<OrderResponse> create(@RequestBody CreateOrderRequest request) {
        OrderResponse order = orderService.create(request);
        return ResponseEntity.created(URI.create("/orders/" + order.orderId())).body(order);
    }

    @GetMapping("/orders/{orderId}")
    @Operation(summary = "주문과 결제 결과 조회")
    public OrderResponse get(@PathVariable String orderId) {
        return orderService.get(orderId);
    }
}
