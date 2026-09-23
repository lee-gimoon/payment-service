package com.example.payment.payment;

import com.example.payment.config.TossProperties;
import com.example.payment.gateway.TossPaymentClient;
import com.example.payment.order.OrderRepository;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;

/** DB의 미확정 결제를 재조회하고, 같은 거래의 잘못된 승인 금액을 확인하면 자동 취소한다. */
@Service
public class PaymentRecoveryService {
    private static final Logger log = LoggerFactory.getLogger(PaymentRecoveryService.class);
    private static final int MAX_ATTEMPTS = 10;
    private final PaymentRepository payments;
    private final OrderRepository orders;
    private final TossPaymentClient toss;
    private final TossProperties properties;

    public PaymentRecoveryService(PaymentRepository payments, OrderRepository orders,
                                  TossPaymentClient toss, TossProperties properties) {
        this.payments = payments;
        this.orders = orders;
        this.toss = toss;
        this.properties = properties;
    }

    /** 브라우저가 닫혀 있어도 실행한다. 한 건의 장애가 다른 주문의 복구를 막지 않는다. */
    public void recoverDuePayments() {
        if (!properties.configured()) return;
        for (Payment candidate : payments.findTop20ByNextActionAtLessThanEqualOrderByNextActionAtAsc(Instant.now())) {
            try {
                recover(candidate.getOrderId());
            } catch (OptimisticLockingFailureException ignored) {
                // 다른 작업이 먼저 선점하거나 완료했다. 다음 조회는 그 결과를 따른다.
            } catch (RuntimeException exception) {
                // 저장 장애·프로세스 종료 후에도 DB의 임대 시간이 지나면 다시 작업한다.
                log.error("PAYMENT_RECOVERY_ERROR orderId={} type={}", candidate.getOrderId(),
                        exception.getClass().getSimpleName());
            }
        }
    }

    private void recover(String orderId) {
        Payment payment = payments.findById(orderId).orElseThrow();
        if (!payment.recoveryDue(Instant.now())) return;
        payment.claimRecovery(Instant.now());
        payment = payments.saveAndFlush(payment); // 이 저장에 성공한 작업자만 외부 API를 호출한다.
        if (payment.getRecoveryAttempts() > MAX_ATTEMPTS) {
            finish(payment, PaymentResult.reviewRequired("RECOVERY_RETRY_EXHAUSTED"));
            return;
        }

        long amount = orders.findById(orderId).orElseThrow().getAmount();
        // 취소 응답을 잃은 경우에도 먼저 GET으로 이미 취소됐는지 확인한다.
        PaymentResult result = toss.lookup(payment, amount);
        if (result.status() == PaymentStatus.CANCEL_PENDING) {
            payment.applyResult(result);
            payment.keepRecoveryLease(Instant.now());
            payment = payments.saveAndFlush(payment); // 취소 대상 금액과 고정 멱등키를 먼저 확정한다.
            result = toss.cancel(payment);
        }
        finish(payment, result);
    }

    private void finish(Payment payment, PaymentResult result) {
        payment.applyResult(result);
        if (payment.needsRecovery() && payment.getRecoveryAttempts() >= MAX_ATTEMPTS) {
            // 마지막 오류 코드와 취소 의도는 보존하고 운영자가 확인할 상태로 전환한다.
            payment.applyResult(PaymentResult.reviewRequired(payment.getErrorCode() == null
                    ? "RECOVERY_RETRY_EXHAUSTED" : payment.getErrorCode()));
        }
        payment = payments.saveAndFlush(payment);
        if (payment.getStatus() == PaymentStatus.REVIEW_REQUIRED) {
            log.error("PAYMENT_REVIEW_REQUIRED orderId={} errorCode={} cancelRequested={}",
                    payment.getOrderId(), payment.getErrorCode(), payment.getCancelIdempotencyKey() != null);
        }
    }
}
