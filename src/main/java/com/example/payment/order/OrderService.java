package com.example.payment.order;

import com.example.payment.api.error.ApiException;
import com.example.payment.payment.Payment;
import com.example.payment.payment.PaymentRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 주문 생성과 결과 조회를 담당한다. 결제 승인은 PaymentService가 담당한다. */
@Service
public class OrderService {
    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;

    public OrderService(OrderRepository orderRepository, PaymentRepository paymentRepository) {
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
    }

    /** 서버가 정한 티셔츠 1장, 10,000원 주문을 저장한다. */
    @Transactional
    public OrderResponse create() {
        PurchaseOrder order = new PurchaseOrder("티셔츠", 1, 10_000);
        order = orderRepository.save(order);
        return OrderResponse.of(order, null);
    }

    /** 주문과 연결된 결제를 조회하여 프론트에 함께 전달한다. */
    @Transactional(readOnly = true)
    public OrderResponse get(String orderId) {
        PurchaseOrder order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "주문을 찾을 수 없습니다."));
        Payment payment = paymentRepository.findById(orderId).orElse(null);
        return OrderResponse.of(order, payment);
    }
}
