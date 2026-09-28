package com.example.payment.payment.application;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
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
