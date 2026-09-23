package com.example.payment.config;

import com.example.payment.payment.PaymentRecoveryService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/** 자동 복구는 기본 활성화된다. 테스트에서는 스케줄러만 끄고 작업을 직접 실행한다. */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "payment.recovery.enabled", havingValue = "true", matchIfMissing = true)
public class PaymentRecoveryConfiguration {
    private final PaymentRecoveryService recovery;

    public PaymentRecoveryConfiguration(PaymentRecoveryService recovery) {
        this.recovery = recovery;
    }

    @Scheduled(fixedDelayString = "${payment.recovery.poll-delay-ms:5000}")
    public void recover() {
        recovery.recoverDuePayments();
    }
}
