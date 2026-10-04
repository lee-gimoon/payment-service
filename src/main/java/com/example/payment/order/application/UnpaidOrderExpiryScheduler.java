package com.example.payment.order.application;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 결제 기한이 지난 결제 대기 주문을 주기(기본 10분, order.unpaid-expiry.interval)마다 취소한다.
 * 기한은 order.unpaid-expiry.after로 정한다.
 */
@Component
@ConditionalOnProperty(name = "order.unpaid-expiry.enabled", havingValue = "true", matchIfMissing = true)
public class UnpaidOrderExpiryScheduler {
    private final UnpaidOrderExpiryService expiry;

    public UnpaidOrderExpiryScheduler(UnpaidOrderExpiryService expiry) {
        this.expiry = expiry;
    }

    @Scheduled(initialDelayString = "PT1M", fixedDelayString = "${order.unpaid-expiry.interval:PT10M}")
    public void cancelExpiredOrders() {
        expiry.cancelExpired();
    }
}
