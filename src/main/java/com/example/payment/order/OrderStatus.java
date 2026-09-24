package com.example.payment.order;

/** 주문 자체의 진행 상태. 결제 시도별 결과는 payment_attempts와 payments에 남긴다. */
public enum OrderStatus {
    PENDING_PAYMENT, CONFIRMED, CANCELED
}
