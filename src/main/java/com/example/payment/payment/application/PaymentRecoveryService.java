package com.example.payment.payment.application;

import com.example.payment.payment.domain.PaymentAttempt;
import com.example.payment.payment.domain.PaymentAttemptStatus;
import com.example.payment.payment.domain.PaymentResult;
import com.example.payment.payment.infrastructure.toss.TossPaymentClient;
import com.example.payment.payment.persistence.PaymentAttemptRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;

/**
 * 결과를 모르는 승인을 PG 조회로 확정한다. 승인을 다시 요청하지는 않는다. 사용자가 다른 주문으로
 * 이미 결제했을 수 있으므로, 인증만 끝난 거래는 PG에서 만료되어 실패로 확정되기를 기다린다.
 */
@Service
public class PaymentRecoveryService {
    private static final Logger log = LoggerFactory.getLogger(PaymentRecoveryService.class);

    // 진행 중인 요청의 즉시 조회와 겹치지 않도록 마지막 확인 뒤 이 시간이 지난 시도만 조회한다.
    static final Duration STALE_AFTER = Duration.ofMinutes(1);
    // 토스 결제 인증은 30분 뒤 만료된다. 그 뒤로도 확정하지 못하면 사람이 확인한다.
    static final Duration REVIEW_AFTER = Duration.ofHours(1);
    private static final int BATCH_SIZE = 50;

    private final PaymentAttemptRepository attempts;
    private final PaymentSettlementService settlement;
    private final TossPaymentClient toss;

    public PaymentRecoveryService(PaymentAttemptRepository attempts, PaymentSettlementService settlement,
                                  TossPaymentClient toss) {
        this.attempts = attempts;
        this.settlement = settlement;
        this.toss = toss;
    }

    public int recoverUnresolved() {
        List<String> ids = attempts.findIdsToRecover(PaymentAttemptStatus.RECOVERABLE,
                Instant.now().minus(STALE_AFTER), Limit.of(BATCH_SIZE));
        for (String id : ids) {
            try {
                recover(id);
            } catch (RuntimeException exception) {
                log.warn("Payment recovery failed: attemptId={}", id, exception);
            }
        }
        return ids.size();
    }

    public void recoverIfStale(PaymentAttempt attempt) {
        if (attempt.needsRecovery(Instant.now().minus(STALE_AFTER))) recover(attempt.getId());
    }

    private void recover(String attemptId) {
        PaymentAttempt attempt = attempts.findById(attemptId).orElse(null);
        if (attempt == null || !attempt.needsRecovery(Instant.now().minus(STALE_AFTER))) return;

        // DB 트랜잭션 밖에서 조회해 PG 응답을 기다리는 동안 주문 잠금을 잡지 않는다.
        PaymentResult result = toss.lookup(attempt.approvalRequest());
        if (result.outcome() == PaymentResult.Outcome.UNKNOWN
                && attempt.getApprovalRequestedAt().isBefore(Instant.now().minus(REVIEW_AFTER))) {
            result = PaymentResult.reviewRequired("PG_RESULT_TIMEOUT");
        }
        settlement.record(attemptId, result);
    }
}
