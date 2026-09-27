package com.example.payment.payment.application;

import com.example.payment.order.OrderResponse;
import com.example.payment.order.OrderService;
import com.example.payment.payment.api.ConfirmPaymentRequest;
import com.example.payment.payment.domain.Payment;
import com.example.payment.payment.domain.PaymentResult;
import com.example.payment.payment.domain.PaymentStatus;
import com.example.payment.payment.infrastructure.toss.TossPaymentClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class PaymentService {
    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final PaymentPreparationService preparation;
    private final PaymentSettlementService settlement;
    private final OrderService orders;
    private final TossPaymentClient toss;

    public PaymentService(PaymentPreparationService preparation, PaymentSettlementService settlement,
                          OrderService orders, TossPaymentClient toss) {
        this.preparation = preparation;
        this.settlement = settlement;
        this.orders = orders;
        this.toss = toss;
    }

    public OrderResponse confirm(ConfirmPaymentRequest request) {
        var prepared = preparation.prepare(request.orderId(), request.paymentKey(), request.attemptId(), request.amount());
        if (!prepared.newPayment()) {
            return orders.get(request.orderId());
        }

        // 준비 트랜잭션을 커밋한 뒤 호출하여 PG 응답을 기다리는 동안 주문 잠금을 유지하지 않는다.
        Payment payment = prepared.payment();
        PaymentResult result = toss.confirm(payment);
        settlement.record(payment.getId(), result);

        if (result.status() == PaymentStatus.UNKNOWN) {
            // 응답 유실은 승인 실패를 뜻하지 않는다. 같은 거래를 한 번 조회해 확인한다.
            result = toss.lookup(payment);
            if (result.status() == PaymentStatus.UNKNOWN) {
                result = PaymentResult.reviewRequired(result.errorCode());
            }
            settlement.record(payment.getId(), result);
        }

        if (result.status() == PaymentStatus.REVIEW_REQUIRED) {
            log.error("Payment requires review: orderId={}, paymentId={}, errorCode={}",
                    payment.getOrderId(), payment.getId(), result.errorCode());
        }
        return orders.get(request.orderId());
    }
}
