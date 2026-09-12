/* 파일 역할: 서버·DB 없이 주문 생성 규칙과 결제 상태 전이 규칙을 검증하는 단위 테스트다. */
package com.example.payment.payment;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.payment.order.PurchaseOrder;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/** 고정 시각으로 작업 만료 경계와 작업 식별자에 따른 결과 반영 규칙을 검증한다. */
class PaymentTest {
    private static final Instant NOW = Instant.parse("2026-09-11T01:00:00Z");

    /** 서버가 고정 상품·수량·금액을 정하고 주문마다 서로 다른 식별자를 생성하는지 확인한다. */
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

    /** 처리 시작 29초에는 재확인을 막고, 30초 경계부터 UNKNOWN으로 표시하여 재확인을 허용하는지 확인한다. */
    @Test
    void abandonedProcessingBecomesRecoverableAtLeaseBoundary() {
        Payment payment = Payment.start("order-123", "payment-key", 10_000, NOW, Duration.ofSeconds(30));
        assertThat(payment.visibleStatus(NOW.plusSeconds(29))).isEqualTo(PaymentStatus.PROCESSING);
        assertThat(payment.canReconcile(NOW.plusSeconds(29))).isFalse();
        assertThat(payment.visibleStatus(NOW.plusSeconds(30))).isEqualTo(PaymentStatus.UNKNOWN);
        assertThat(payment.canReconcile(NOW.plusSeconds(30))).isTrue();
    }

    /** 이전 작업의 늦은 응답이 새 재확인 작업이나 성공 결과를 덮어쓰지 못하고 시도 식별자는 유지되는지 확인한다. */
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

    /** 한 작업의 결과를 이미 반영했다면 동일 작업 식별자로 온 다른 결과를 다시 반영하지 않는지 확인한다. */
    @Test
    void completedOperationCannotBeAppliedTwice() {
        Payment payment = Payment.start("order-123", "payment-key", 10_000, NOW, Duration.ofSeconds(30));
        payment.complete(payment.getOperationId(), PaymentOutcome.unknown("PG_COMMUNICATION_ERROR"), NOW);
        payment.complete(payment.getOperationId(), PaymentOutcome.failed("REJECT_CARD_COMPANY"), NOW);
        assertThat(payment.visibleStatus(NOW)).isEqualTo(PaymentStatus.UNKNOWN);
    }
}
