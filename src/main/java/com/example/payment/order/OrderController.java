/* 파일 역할: React가 보낸 /orders HTTP 요청을 주문 서비스에 연결하는 API 진입점이다. */
package com.example.payment.order;

import com.example.payment.api.error.ApiException;
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

/** 주문 생성·조회 요청을 받고, 입력 규칙에 맞는 HTTP 응답과 주문 데이터를 반환한다. */
@RestController
@Tag(name = "Orders", description = "주문 생성 및 조회")
public class OrderController {
    private final OrderService orders;

    /** Spring이 생성한 주문 서비스를 주입받아 요청 처리에 사용한다. */
    public OrderController(OrderService orders) { this.orders = orders; }

    /**
     * POST /orders: 클라이언트의 가격·수량 지정을 거부하고 서버 기준으로 주문을 만든다.
     * 생성된 주문과 HTTP 201, 새 주문의 조회 주소인 Location 헤더를 반환한다.
     */
    @PostMapping("/orders")
    @Operation(summary = "주문 생성", description = "서버가 정한 상품·수량·가격으로 새로운 주문을 생성합니다. 요청 본문은 비워두거나 빈 객체를 사용합니다.")
    ResponseEntity<OrderResponse> create(@RequestBody(required = false) Map<String, Object> request) {
        if (request != null && !request.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "주문 상품, 수량과 가격은 서버에서 정합니다. 빈 본문으로 요청해주세요.");
        }
        OrderResponse order = orders.create();
        return ResponseEntity.created(URI.create("/orders/" + order.orderId())).body(order);
    }

    /** GET /orders/{orderId}: 우리 DB에 저장된 주문과 결제 상태를 조회한다. PG 조회는 수행하지 않는다. */
    @GetMapping("/orders/{orderId}")
    @Operation(summary = "주문 조회", description = "저장된 주문과 현재 결제 상태를 조회합니다. PG사에는 요청하지 않습니다.")
    OrderResponse get(@PathVariable @Pattern(regexp = "[a-zA-Z0-9_-]{6,64}") String orderId) {
        return orders.get(orderId);
    }
}
