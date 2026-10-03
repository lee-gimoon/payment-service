package com.example.payment.order.domain;

public enum OrderStatus {
    PENDING_PAYMENT, PAYMENT_IN_PROGRESS, PAID,
    /** 결제 기한 안에 결제하지 않아 자동 취소된 주문. 다시 결제할 수 없다. */
    CANCELED
}
