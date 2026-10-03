package com.example.payment.order;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 관리자 주문 관리. /admin/** 경로라 shop-admin 역할이 있어야 호출할 수 있다. */
@RestController
@Tag(name = "Admin orders")
public class AdminOrderController {
    private final AdminOrderService orders;

    public AdminOrderController(AdminOrderService orders) {
        this.orders = orders;
    }

    @GetMapping("/admin/orders")
    @Operation(summary = "결제 완료 주문 목록 (배송 단계로 거르기, 최대 100건)")
    public List<AdminOrderSummary> list(@RequestParam(required = false) DeliveryStatus delivery) {
        return orders.list(delivery);
    }

    @GetMapping("/admin/orders/summary")
    @Operation(summary = "배송 준비·배송 중 주문 수")
    public AdminOrderCounts counts() {
        return orders.counts();
    }

    @GetMapping("/admin/orders/{orderId}")
    @Operation(summary = "주문 상세 (상품, 배송지, 배송 상태)")
    public AdminOrderResponse get(@PathVariable String orderId) {
        return orders.get(orderId);
    }

    @PutMapping("/admin/orders/{orderId}/shipment")
    @Operation(summary = "송장 등록·수정 (배송 중으로 변경)")
    public AdminOrderResponse ship(@PathVariable String orderId, @Valid @RequestBody ShipRequest request) {
        return orders.ship(orderId, request);
    }

    @PostMapping("/admin/orders/{orderId}/shipment/delivered")
    @Operation(summary = "배송 완료 처리")
    public AdminOrderResponse markDelivered(@PathVariable String orderId) {
        return orders.markDelivered(orderId);
    }
}
