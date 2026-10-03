package com.example.payment.payment.application;

import com.example.payment.order.OrderItem;
import com.example.payment.order.OrderItemRepository;
import com.example.payment.order.OrderRepository;
import com.example.payment.order.PurchaseOrder;
import com.example.payment.payment.domain.Payment;
import com.example.payment.payment.domain.PaymentAttempt;
import com.example.payment.payment.domain.PaymentAttemptStatus;
import com.example.payment.payment.domain.PaymentResult;
import com.example.payment.payment.persistence.PaymentAttemptRepository;
import com.example.payment.payment.persistence.PaymentRepository;
import com.example.payment.product.ProductInventory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** PG 결과를 시도·주문·결제 기록에 함께 반영한다. 승인 요청과 복구가 모두 이 경로를 쓴다. */
@Service
public class PaymentSettlementService {
    private static final Logger log = LoggerFactory.getLogger(PaymentSettlementService.class);

    private final PaymentAttemptRepository attempts;
    private final PaymentRepository payments;
    private final OrderRepository orders;
    private final OrderItemRepository orderItems;
    private final ProductInventory inventory;

    public PaymentSettlementService(PaymentAttemptRepository attempts, PaymentRepository payments,
                                    OrderRepository orders, OrderItemRepository orderItems,
                                    ProductInventory inventory) {
        this.attempts = attempts;
        this.payments = payments;
        this.orders = orders;
        this.orderItems = orderItems;
        this.inventory = inventory;
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
                    // 시도·주문 상태와 같은 트랜잭션에서 실제 결제 기록을 남긴다.
                    payments.save(Payment.approved(attempt));
                } else {
                    attempt.requireReview(new PaymentResult(PaymentResult.Outcome.REVIEW_REQUIRED, result.pgStatus(),
                            "PG_EVIDENCE_MISMATCH", result.approvedAt(), result.pgAmount(), result.pgCurrency()));
                }
            }
            case FAILED -> {
                attempt.fail(result);
                order.releaseApproval(attempt.getId());
                // 승인 관문에서 가져간 재고를 슬롯과 함께 돌려준다. 결과를 모르는 동안에는 돌려주지 않는다.
                inventory.putBack(orderItems.findByOrderId(orderId).stream().map(OrderItem::stockLine).toList());
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
