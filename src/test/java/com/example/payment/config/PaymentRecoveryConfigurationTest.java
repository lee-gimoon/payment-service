package com.example.payment.config;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.example.payment.payment.PaymentRecoveryService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class PaymentRecoveryConfigurationTest {
    @Test
    void recoveryRunsWithoutAnyHttpRequest() {
        PaymentRecoveryService recovery = mock(PaymentRecoveryService.class);
        new ApplicationContextRunner().withUserConfiguration(PaymentRecoveryConfiguration.class)
                .withBean(PaymentRecoveryService.class, () -> recovery)
                .withPropertyValues("payment.recovery.poll-delay-ms=20")
                .run(context -> verify(recovery, timeout(2000).atLeastOnce()).recoverDuePayments());
    }

    @Test
    void testsCanDisableOnlyTheAutomaticTrigger() {
        PaymentRecoveryService recovery = mock(PaymentRecoveryService.class);
        new ApplicationContextRunner().withUserConfiguration(PaymentRecoveryConfiguration.class)
                .withBean(PaymentRecoveryService.class, () -> recovery)
                .withPropertyValues("payment.recovery.enabled=false")
                .run(context -> verifyNoInteractions(recovery));
    }
}
