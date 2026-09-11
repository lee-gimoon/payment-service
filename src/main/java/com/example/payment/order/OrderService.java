package com.example.payment.order;

import com.example.payment.api.ApiException;
import com.example.payment.payment.PaymentRepository;
import java.time.Clock;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {
    private final OrderRepository orders;
    private final PaymentRepository payments;
    private final Clock clock;

    public OrderService(OrderRepository orders, PaymentRepository payments, Clock clock) {
        this.orders = orders;
        this.payments = payments;
        this.clock = clock;
    }

    @Transactional
    public OrderResponse create() {
        PurchaseOrder order = orders.save(PurchaseOrder.tShirt(clock.instant()));
        return OrderResponse.of(order, null, clock.instant());
    }

    @Transactional(readOnly = true)
    public OrderResponse get(String orderId) {
        PurchaseOrder order = orders.findById(orderId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "주문을 찾을 수 없습니다."));
        return OrderResponse.of(order, payments.findById(orderId).orElse(null), clock.instant());
    }
}
