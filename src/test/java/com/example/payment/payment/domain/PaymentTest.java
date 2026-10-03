package com.example.payment.payment.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.payment.order.domain.TestOrders;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class PaymentTest {
    private static final Instant APPROVED_AT = Instant.parse("2026-09-11T01:00:00Z");

    @Test
    void onlySucceededAttemptBecomesPayment() {
        PaymentAttempt attempt = PaymentAttempt.start(TestOrders.order(19_000));
        attempt.requestApproval("payment-key");
        assertThatThrownBy(() -> Payment.approved(attempt)).isInstanceOf(IllegalStateException.class);

        attempt.succeed(new PaymentResult(PaymentResult.Outcome.SUCCEEDED, "DONE", null, APPROVED_AT,
                BigDecimal.valueOf(19_000), "KRW"));
        Payment payment = Payment.approved(attempt);

        assertThat(payment.getOrderId()).isEqualTo(attempt.getOrderId());
        assertThat(payment.getAttemptId()).isEqualTo(attempt.getId());
        assertThat(payment.getPaymentKey()).isEqualTo("payment-key");
        assertThat(payment.getAmount()).isEqualTo(19_000);
        assertThat(payment.getCurrency()).isEqualTo("KRW");
        assertThat(payment.getApprovedAt()).isEqualTo(APPROVED_AT);
    }

    @Test
    void failedAttemptIsNotAPayment() {
        PaymentAttempt attempt = PaymentAttempt.start(TestOrders.order(19_000));
        attempt.requestApproval("payment-key");
        attempt.fail(PaymentResult.failed("REJECT_CARD_COMPANY"));

        assertThatThrownBy(() -> Payment.approved(attempt)).isInstanceOf(IllegalStateException.class);
    }
}
