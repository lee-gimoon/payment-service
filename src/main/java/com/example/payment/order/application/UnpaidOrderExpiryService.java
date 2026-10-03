package com.example.payment.order.application;

import com.example.payment.order.domain.OrderStatus;
import com.example.payment.order.domain.PurchaseOrder;
import com.example.payment.order.persistence.OrderRepository;
import com.example.payment.payment.persistence.PaymentAttemptRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 결제 기한이 지난 결제 대기 주문을 취소한다. 승인 중이거나 결과를 모르는 주문, 결제 완료 주문은 건드리지 않는다.
 * 재고는 승인 관문에서만 가져가므로 돌려줄 것이 없다.
 */
@Service
public class UnpaidOrderExpiryService {
    private static final Logger log = LoggerFactory.getLogger(UnpaidOrderExpiryService.class);

    // 토스 결제 인증은 30분 뒤 만료된다. 그 안에 결제창을 연 주문은 고객이 인증 중일 수 있어 기다린다.
    static final Duration AUTHENTICATION_WINDOW = Duration.ofMinutes(30);
    private static final int BATCH_SIZE = 100;

    private final OrderRepository orders;
    private final PaymentAttemptRepository attempts;
    private final TransactionTemplate transaction;
    private final Duration unpaidAfter;

    public UnpaidOrderExpiryService(OrderRepository orders, PaymentAttemptRepository attempts,
                                    TransactionTemplate transaction,
                                    @Value("${order.unpaid-expiry.after:PT24H}") Duration unpaidAfter) {
        if (unpaidAfter.isNegative() || unpaidAfter.isZero()) {
            throw new IllegalArgumentException("order.unpaid-expiry.after는 0보다 커야 합니다.");
        }
        this.orders = orders;
        this.attempts = attempts;
        this.transaction = transaction;
        this.unpaidAfter = unpaidAfter;
    }

    /** @return 이번에 취소한 주문 수 */
    public int cancelExpired() {
        Instant now = Instant.now();
        List<String> ids = orders.findUnpaidIdsToCancel(now.minus(unpaidAfter), now.minus(AUTHENTICATION_WINDOW),
                Limit.of(BATCH_SIZE));
        int canceled = 0;
        for (String id : ids) {
            try {
                // 주문마다 따로 커밋해 한 건이 실패해도 나머지는 취소한다.
                if (Boolean.TRUE.equals(transaction.execute(status -> cancelIfExpired(id)))) canceled++;
            } catch (RuntimeException exception) {
                log.warn("Unpaid order cancellation failed: orderId={}", id, exception);
            }
        }
        if (canceled > 0) log.info("Canceled unpaid orders: count={}", canceled);
        return canceled;
    }

    // 결제 시작·승인 관문과 같은 주문 잠금 아래에서 다시 확인한다. 그사이 결제창을 열었거나 승인이 시작됐으면 그대로 둔다.
    private boolean cancelIfExpired(String orderId) {
        PurchaseOrder order = orders.findByIdForUpdate(orderId).orElse(null);
        Instant now = Instant.now();
        if (order == null || order.getStatus() != OrderStatus.PENDING_PAYMENT
                || !order.getCreatedAt().isBefore(now.minus(unpaidAfter))) {
            return false;
        }
        boolean authenticating = attempts.findFirstByOrderIdOrderByStartedAtDesc(orderId)
                .map(attempt -> !attempt.getStartedAt().isBefore(now.minus(AUTHENTICATION_WINDOW)))
                .orElse(false);
        if (authenticating) return false;
        order.cancelUnpaid();
        return true;
    }
}
