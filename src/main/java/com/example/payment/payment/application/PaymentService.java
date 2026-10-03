package com.example.payment.payment.application;

import com.example.payment.order.api.OrderResponse;
import com.example.payment.order.application.OrderService;
import com.example.payment.payment.api.ConfirmPaymentRequest;
import com.example.payment.payment.domain.ApprovalRequest;
import com.example.payment.payment.domain.PaymentAttempt;
import com.example.payment.payment.domain.PaymentResult;
import com.example.payment.payment.infrastructure.toss.TossPaymentClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class PaymentService {
    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final PaymentPreparationService preparation;
    private final PaymentSettlementService settlement;
    private final PaymentRecoveryService recovery;
    private final OrderService orders;
    private final TossPaymentClient toss;

    public PaymentService(PaymentPreparationService preparation, PaymentSettlementService settlement,
                          PaymentRecoveryService recovery, OrderService orders, TossPaymentClient toss) {
        this.preparation = preparation;
        this.settlement = settlement;
        this.recovery = recovery;
        this.orders = orders;
        this.toss = toss;
    }

    public OrderResponse confirm(ConfirmPaymentRequest request, String customerId) {
        var prepared = preparation.prepare(request.orderId(), customerId, request.paymentKey(),
                request.attemptId(), request.amount());
        PaymentAttempt attempt = prepared.attempt();
        if (!prepared.newApproval()) {
            // 같은 결제 키의 재요청은 승인을 다시 호출하지 않는다. 오래 미확정인 시도만 PG 조회로 확인한다.
            recovery.recoverIfStale(attempt);
            return orders.get(request.orderId(), customerId);
        }

        // 관문 트랜잭션을 커밋한 뒤 호출하여 PG 응답을 기다리는 동안 주문 잠금을 유지하지 않는다.
        ApprovalRequest approval = attempt.approvalRequest();
        PaymentResult result = toss.confirm(approval);
        settlement.record(attempt.getId(), result);

        if (result.outcome() == PaymentResult.Outcome.UNKNOWN) {
            // 응답 유실은 승인 실패를 뜻하지 않는다. 같은 거래를 한 번 조회하고, 그래도 모르면 복구 작업이 이어서 확인한다.
            result = toss.lookup(approval);
            settlement.record(attempt.getId(), result);
            if (result.outcome() == PaymentResult.Outcome.UNKNOWN) {
                log.warn("Payment result unresolved: orderId={}, attemptId={}, errorCode={}",
                        approval.orderId(), approval.attemptId(), result.errorCode());
            }
        }
        return orders.get(request.orderId(), customerId);
    }
}
