package com.example.payment.payment.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.payment.order.TestOrders;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class PaymentAttemptTest {
    private static final Instant APPROVED_AT = Instant.parse("2026-09-11T01:00:00Z");

    @Test
    void attemptCopiesServerAmountAndStartsWithoutApproval() {
        PaymentAttempt attempt = PaymentAttempt.start(TestOrders.order(19_000));

        assertThat(attempt.getStatus()).isEqualTo(PaymentAttemptStatus.STARTED);
        assertThat(attempt.getAmount()).isEqualTo(19_000);
        assertThat(attempt.getCurrency()).isEqualTo("KRW");
        assertThat(attempt.getPaymentKey()).isNull();
        assertThatThrownBy(attempt::approvalRequest).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void approvalCanBeRequestedOnlyOnceWithOnePaymentKey() {
        PaymentAttempt attempt = approving();

        assertThat(attempt.getStatus()).isEqualTo(PaymentAttemptStatus.APPROVING);
        assertThat(attempt.approvalRequest())
                .isEqualTo(new ApprovalRequest(attempt.getId(), attempt.getOrderId(), "payment-key", 19_000, "KRW"));
        assertThatThrownBy(() -> attempt.requestApproval("other-key")).isInstanceOf(IllegalStateException.class);
        assertThat(attempt.getPaymentKey()).isEqualTo("payment-key");
    }

    @Test
    void lateAuthenticationEventsDoNotOverwriteApproval() {
        PaymentAttempt attempt = approving();
        attempt.authenticationCanceled("WINDOW_CLOSED");
        assertThat(attempt.getStatus()).isEqualTo(PaymentAttemptStatus.APPROVING);

        PaymentAttempt canceled = PaymentAttempt.start(TestOrders.order(19_000));
        canceled.authenticationCanceled("WINDOW_CLOSED");
        assertThatThrownBy(() -> canceled.requestApproval("payment-key")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void successRequiresEvidenceMatchingTheStoredAmount() {
        PaymentAttempt attempt = approving();
        assertThat(attempt.matchesApproval(done(18_000, "KRW"))).isFalse();
        assertThat(attempt.matchesApproval(done(19_000, "USD"))).isFalse();
        assertThatThrownBy(() -> attempt.succeed(done(18_000, "KRW"))).isInstanceOf(IllegalArgumentException.class);
        assertThat(attempt.getStatus()).isEqualTo(PaymentAttemptStatus.APPROVING);

        attempt.succeed(done(19_000, "KRW"));
        assertThat(attempt.getStatus()).isEqualTo(PaymentAttemptStatus.SUCCEEDED);
        assertThat(attempt.getPgApprovedAt()).isEqualTo(APPROVED_AT);
        assertThat(attempt.getFinishedAt()).isNotNull();
    }

    @Test
    void confirmedResultsNeverChange() {
        PaymentAttempt succeeded = approving();
        succeeded.succeed(done(19_000, "KRW"));
        assertThatThrownBy(() -> succeeded.fail(PaymentResult.failed("PG_EXPIRED"))).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> succeeded.markUnknown(PaymentResult.unknown("PG_LOOKUP_ERROR")))
                .isInstanceOf(IllegalStateException.class);
        assertThat(succeeded.getStatus()).isEqualTo(PaymentAttemptStatus.SUCCEEDED);

        PaymentAttempt failed = approving();
        failed.fail(PaymentResult.failed("REJECT_CARD_COMPANY"));
        assertThatThrownBy(() -> failed.succeed(done(19_000, "KRW"))).isInstanceOf(IllegalStateException.class);
        assertThat(failed.getStatus()).isEqualTo(PaymentAttemptStatus.FAILED);
    }

    @Test
    void unknownResultStaysLiveUntilPgConfirmsIt() {
        PaymentAttempt attempt = approving();
        attempt.markUnknown(PaymentResult.unknown("PG_COMMUNICATION_ERROR"));

        assertThat(attempt.getStatus()).isEqualTo(PaymentAttemptStatus.UNKNOWN);
        assertThat(attempt.isAwaitingResult()).isTrue();
        assertThat(attempt.getLastCheckedAt()).isNotNull();
        attempt.succeed(done(19_000, "KRW"));
        assertThat(attempt.getStatus()).isEqualTo(PaymentAttemptStatus.SUCCEEDED);
    }

    @Test
    void reviewIsNotDowngradedByLaterUnknownResults() {
        PaymentAttempt attempt = approving();
        attempt.requireReview(new PaymentResult(PaymentResult.Outcome.REVIEW_REQUIRED, "DONE", "PG_AMOUNT_MISMATCH",
                APPROVED_AT, BigDecimal.valueOf(18_000), "KRW"));
        attempt.markUnknown(PaymentResult.unknown("PG_LOOKUP_ERROR"));

        assertThat(attempt.getStatus()).isEqualTo(PaymentAttemptStatus.REVIEW_REQUIRED);
        assertThat(attempt.getPgAmount()).isEqualByComparingTo("18000");
        assertThat(attempt.needsRecovery(Instant.now().plusSeconds(3600))).isFalse();
    }

    @Test
    void onlyStaleApprovingOrUnknownAttemptsNeedRecovery() {
        PaymentAttempt attempt = approving();
        assertThat(attempt.needsRecovery(Instant.now().minusSeconds(60))).isFalse();
        assertThat(attempt.needsRecovery(Instant.now().plusSeconds(60))).isTrue();

        PaymentAttempt started = PaymentAttempt.start(TestOrders.order(19_000));
        assertThat(started.needsRecovery(Instant.now().plusSeconds(60))).isFalse();
    }

    private static PaymentAttempt approving() {
        PaymentAttempt attempt = PaymentAttempt.start(TestOrders.order(19_000));
        attempt.requestApproval("payment-key");
        return attempt;
    }

    private static PaymentResult done(long amount, String currency) {
        return new PaymentResult(PaymentResult.Outcome.SUCCEEDED, "DONE", null, APPROVED_AT,
                BigDecimal.valueOf(amount), currency);
    }
}
