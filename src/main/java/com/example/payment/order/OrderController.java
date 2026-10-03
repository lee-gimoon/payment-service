package com.example.payment.order;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Orders")
public class OrderController {
    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping("/orders")
    @Operation(summary = "주문 생성")
    public ResponseEntity<OrderResponse> create(@AuthenticationPrincipal Jwt customer,
                                                @RequestBody CreateOrderRequest request) {
        OrderResponse order = orderService.create(request, customer.getSubject());
        return ResponseEntity.created(URI.create("/orders/" + order.orderId())).body(order);
    }

    @GetMapping("/me/orders")
    @Operation(summary = "내 주문 목록 (최근 50건)")
    public List<OrderSummaryResponse> mine(@AuthenticationPrincipal Jwt customer) {
        return orderService.listMine(customer.getSubject());
    }

    @GetMapping("/orders/{orderId}")
    @Operation(summary = "주문과 결제 결과 조회")
    public OrderResponse get(@AuthenticationPrincipal Jwt customer, @PathVariable String orderId) {
        return orderService.get(orderId, customer.getSubject());
    }
}
