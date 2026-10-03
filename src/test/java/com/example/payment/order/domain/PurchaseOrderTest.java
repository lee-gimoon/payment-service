package com.example.payment.order.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PurchaseOrderTest {
    @Test
    void orderBelongsOnlyToTheCustomerWhoPlacedIt() {
        PurchaseOrder order = TestOrders.order(19_000);

        assertThat(order.isOwnedBy(TestOrders.CUSTOMER_ID)).isTrue();
        assertThat(order.isOwnedBy("customer-2")).isFalse();
        assertThat(order.isOwnedBy(null)).isFalse();
        assertThatThrownBy(() -> new PurchaseOrder(" ", "선데이 크루 티", 1, 1, 19_000, TestOrders.SHIPPING))
                .isInstanceOf(IllegalArgumentException.class);
        // 새 주문은 배송지 없이 만들 수 없다.
        assertThatThrownBy(() -> new PurchaseOrder(TestOrders.CUSTOMER_ID, "선데이 크루 티", 1, 1, 19_000, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void onlyOneAttemptCanHoldTheApprovalSlot() {
        PurchaseOrder order = TestOrders.order(19_000);
        order.claimApproval("attempt-1");

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAYMENT_IN_PROGRESS);
        assertThat(order.acceptsNewPayment()).isFalse();
        assertThatThrownBy(() -> order.claimApproval("attempt-2")).isInstanceOf(IllegalStateException.class);
        assertThat(order.getApprovalAttemptId()).isEqualTo("attempt-1");
    }

    @Test
    void confirmedFailureReleasesTheSlotForANewPayment() {
        PurchaseOrder order = TestOrders.order(19_000);
        order.claimApproval("attempt-1");

        assertThatThrownBy(() -> order.releaseApproval("attempt-2")).isInstanceOf(IllegalStateException.class);
        order.releaseApproval("attempt-1");

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(order.getApprovalAttemptId()).isNull();
        order.claimApproval("attempt-2");
        assertThat(order.getApprovalAttemptId()).isEqualTo("attempt-2");
    }

    @Test
    void paidOrderKeepsTheSuccessfulAttemptAndRejectsEverythingElse() {
        PurchaseOrder order = TestOrders.order(19_000);
        order.claimApproval("attempt-1");
        order.markPaid("attempt-1");
        order.markPaid("attempt-1");

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(order.getPaidAt()).isNotNull();
        assertThat(order.acceptsNewPayment()).isFalse();
        assertThatThrownBy(() -> order.markPaid("attempt-2")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> order.releaseApproval("attempt-1")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> order.claimApproval("attempt-2")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void onlyAnUnpaidOrderCanBeCanceledAndItTakesNoNewPayment() {
        PurchaseOrder order = TestOrders.order(19_000);
        order.cancelUnpaid();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELED);
        assertThat(order.isCanceled()).isTrue();
        assertThat(order.getCanceledAt()).isNotNull();
        assertThat(order.acceptsNewPayment()).isFalse();
        assertThatThrownBy(() -> order.claimApproval("attempt-1")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(order::cancelUnpaid).isInstanceOf(IllegalStateException.class);

        // 승인 중이거나 결제된 주문은 돈이 나갔을 수 있어 취소하지 않는다.
        PurchaseOrder approving = TestOrders.order(19_000);
        approving.claimApproval("attempt-1");
        assertThatThrownBy(approving::cancelUnpaid).isInstanceOf(IllegalStateException.class);
        approving.markPaid("attempt-1");
        assertThatThrownBy(approving::cancelUnpaid).isInstanceOf(IllegalStateException.class);
        assertThat(approving.getCanceledAt()).isNull();
    }

    @Test
    void orderCannotBePaidWithoutAnApprovalInProgress() {
        PurchaseOrder order = TestOrders.order(19_000);
        assertThatThrownBy(() -> order.markPaid("attempt-1")).isInstanceOf(IllegalStateException.class);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
    }
}
