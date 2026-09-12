/* 파일 역할: 주문 API와 주문·결제 저장소 사이에서 주문 생성 및 조회 흐름을 처리한다. */
package com.example.payment.order;

import com.example.payment.api.error.ApiException;
import com.example.payment.payment.PaymentRepository;
import java.time.Clock;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 서버가 정한 상품으로 주문을 저장하고, 주문과 결제를 조합해 응답을 만드는 업무 서비스다. */
@Service
public class OrderService {
    private final OrderRepository orders;
    private final PaymentRepository payments;
    private final Clock clock;

    /** 주문·결제 저장소와 현재 시각을 제공하는 Clock을 주입받는다. */
    public OrderService(OrderRepository orders, PaymentRepository payments, Clock clock) {
        this.orders = orders;
        this.payments = payments;
        this.clock = clock;
    }

    /** 티셔츠 1장, 10,000원 주문을 트랜잭션 안에서 저장하고 승인 시도 전인 READY 응답을 만든다. */
    @Transactional
    public OrderResponse create() {
        PurchaseOrder order = orders.save(PurchaseOrder.tShirt(clock.instant()));
        return OrderResponse.of(order, null, clock.instant());
    }

    /** 주문이 없으면 404 오류를 발생시키고, 있으면 연결된 결제의 현재 표시 상태까지 조회한다. */
    @Transactional(readOnly = true)
    public OrderResponse get(String orderId) {
        PurchaseOrder order = orders.findById(orderId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "주문을 찾을 수 없습니다."));
        return OrderResponse.of(order, payments.findById(orderId).orElse(null), clock.instant());
    }
}
