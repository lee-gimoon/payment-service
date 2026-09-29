package com.example.payment.order;

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
        assertThatThrownBy(() -> new PurchaseOrder(" ", "선데이 크루 티", 1, 1, 19_000))
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
    void orderCannotBePaidWithoutAnApprovalInProgress() {
        PurchaseOrder order = TestOrders.order(19_000);
        assertThatThrownBy(() -> order.markPaid("attempt-1")).isInstanceOf(IllegalStateException.class);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
    }
}
