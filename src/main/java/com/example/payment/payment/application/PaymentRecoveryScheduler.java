package com.example.payment.payment.application;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 결과를 모르는 승인을 1분마다 토스 조회로 확정한다. */
@Component
@ConditionalOnProperty(name = "payment.recovery.enabled", havingValue = "true", matchIfMissing = true)
public class PaymentRecoveryScheduler {
    private final PaymentRecoveryService recovery;

    public PaymentRecoveryScheduler(PaymentRecoveryService recovery) {
        this.recovery = recovery;
    }

    @Scheduled(initialDelayString = "PT1M", fixedDelayString = "PT1M")
    public void recoverUnresolvedPayments() {
        recovery.recoverUnresolved();
    }
}
