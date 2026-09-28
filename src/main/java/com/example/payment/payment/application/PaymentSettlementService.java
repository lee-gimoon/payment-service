package com.example.payment.payment.application;

import com.example.payment.order.OrderRepository;
import com.example.payment.order.PurchaseOrder;
import com.example.payment.payment.domain.PaymentAttempt;
import com.example.payment.payment.domain.PaymentAttemptStatus;
import com.example.payment.payment.domain.PaymentResult;
import com.example.payment.payment.persistence.PaymentAttemptRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** PG 결과를 시도와 주문에 함께 기록한다. 승인 요청과 복구가 모두 이 경로를 쓴다. */
@Service
public class PaymentSettlementService {
    private static final Logger log = LoggerFactory.getLogger(PaymentSettlementService.class);

    private final PaymentAttemptRepository attempts;
    private final OrderRepository orders;

    public PaymentSettlementService(PaymentAttemptRepository attempts, OrderRepository orders) {
        this.attempts = attempts;
        this.orders = orders;
    }

    @Transactional
    public void record(String attemptId, PaymentResult result) {
        String orderId = attempts.findOrderIdById(attemptId).orElseThrow();
        PurchaseOrder order = orders.findByIdForUpdate(orderId).orElseThrow();
        PaymentAttempt attempt = attempts.findById(attemptId).orElseThrow();
        // 이미 확정된 결과는 늦게 도착한 응답이나 중복 복구로 바꾸지 않는다.
        if (!attempt.isAwaitingResult()) return;

        switch (result.outcome()) {
            case SUCCEEDED -> {
                if (attempt.matchesApproval(result)) {
                    attempt.succeed(result);
                    order.markPaid(attempt.getId());
                } else {
                    attempt.requireReview(new PaymentResult(PaymentResult.Outcome.REVIEW_REQUIRED, result.pgStatus(),
                            "PG_EVIDENCE_MISMATCH", result.approvedAt(), result.pgAmount(), result.pgCurrency()));
                }
            }
            case FAILED -> {
                attempt.fail(result);
                order.releaseApproval(attempt.getId());
            }
            case UNKNOWN -> attempt.markUnknown(result);
            case REVIEW_REQUIRED -> attempt.requireReview(result);
        }

        if (attempt.getStatus() == PaymentAttemptStatus.REVIEW_REQUIRED) {
            log.error("Payment requires review: orderId={}, attemptId={}, errorCode={}",
                    orderId, attemptId, attempt.getErrorCode());
        }
    }
}
