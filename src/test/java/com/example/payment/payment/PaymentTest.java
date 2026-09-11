package com.example.payment.payment;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.payment.order.PurchaseOrder;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class PaymentTest {
    private static final Instant NOW = Instant.parse("2026-09-11T01:00:00Z");

    @Test
    void serverDeterminesOrderAndGeneratesUniqueIds() {
        PurchaseOrder first = PurchaseOrder.tShirt(NOW);
        PurchaseOrder second = PurchaseOrder.tShirt(NOW);
        assertThat(first.getId()).matches("[a-zA-Z0-9_-]{6,64}").isNotEqualTo(second.getId());
        assertThat(first.getAmount()).isEqualTo(10_000);
        assertThat(first.getQuantity()).isOne();
        assertThat(first.getCurrency()).isEqualTo("KRW");
        assertThat(first.getCreatedAt()).isEqualTo(NOW);
    }

    @Test
    void abandonedProcessingBecomesRecoverableAtLeaseBoundary() {
        Payment payment = Payment.start("order-123", "payment-key", 10_000, NOW, Duration.ofSeconds(30));
        assertThat(payment.visibleStatus(NOW.plusSeconds(29))).isEqualTo(PaymentStatus.PROCESSING);
        assertThat(payment.canReconcile(NOW.plusSeconds(29))).isFalse();
        assertThat(payment.visibleStatus(NOW.plusSeconds(30))).isEqualTo(PaymentStatus.UNKNOWN);
        assertThat(payment.canReconcile(NOW.plusSeconds(30))).isTrue();
    }

    @Test
    void oldOperationCannotOverwriteNewReconciliation() {
        Payment payment = Payment.start("order-123", "payment-key", 10_000, NOW, Duration.ofSeconds(30));
        String previousOperation = payment.getOperationId();
        String attempt = payment.getAttemptId();
        payment.beginOperation(NOW.plusSeconds(30), Duration.ofSeconds(30));
        payment.complete(previousOperation, PaymentOutcome.failed("REJECT_CARD_COMPANY"), NOW.plusSeconds(31));
        assertThat(payment.visibleStatus(NOW.plusSeconds(31))).isEqualTo(PaymentStatus.PROCESSING);
        assertThat(payment.getAttemptId()).isEqualTo(attempt);
        payment.complete(payment.getOperationId(), new PaymentOutcome(PaymentStatus.SUCCEEDED, "DONE", null, NOW), NOW.plusSeconds(32));
        payment.complete(previousOperation, PaymentOutcome.unknown("PG_COMMUNICATION_ERROR"), NOW.plusSeconds(33));
        assertThat(payment.visibleStatus(NOW.plusSeconds(33))).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(payment.getApprovedAt()).isEqualTo(NOW);
        assertThat(payment.canReconcile(NOW.plusSeconds(60))).isFalse();
    }

    @Test
    void completedOperationCannotBeAppliedTwice() {
        Payment payment = Payment.start("order-123", "payment-key", 10_000, NOW, Duration.ofSeconds(30));
        payment.complete(payment.getOperationId(), PaymentOutcome.unknown("PG_COMMUNICATION_ERROR"), NOW);
        payment.complete(payment.getOperationId(), PaymentOutcome.failed("REJECT_CARD_COMPANY"), NOW);
        assertThat(payment.visibleStatus(NOW)).isEqualTo(PaymentStatus.UNKNOWN);
    }
}
