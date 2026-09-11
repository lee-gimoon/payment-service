package com.example.payment.order;

import com.example.payment.api.ApiException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Orders", description = "주문 생성 및 조회")
public class OrderController {
    private final OrderService orders;

    public OrderController(OrderService orders) { this.orders = orders; }

    @PostMapping("/orders")
    @Operation(summary = "주문 생성", description = "서버가 정한 상품·수량·가격으로 새로운 주문을 생성합니다. 요청 본문은 비워두거나 빈 객체를 사용합니다.")
    ResponseEntity<OrderResponse> create(@RequestBody(required = false) Map<String, Object> request) {
        if (request != null && !request.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "주문 상품, 수량과 가격은 서버에서 정합니다. 빈 본문으로 요청해주세요.");
        }
        OrderResponse order = orders.create();
        return ResponseEntity.created(URI.create("/orders/" + order.orderId())).body(order);
    }

    @GetMapping("/orders/{orderId}")
    @Operation(summary = "주문 조회", description = "저장된 주문과 현재 결제 상태를 조회합니다. PG사에는 요청하지 않습니다.")
    OrderResponse get(@PathVariable @Pattern(regexp = "[a-zA-Z0-9_-]{6,64}") String orderId) {
        return orders.get(orderId);
    }
}
