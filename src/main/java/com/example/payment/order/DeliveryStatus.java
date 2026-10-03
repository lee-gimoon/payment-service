package com.example.payment.order;

/** 고객과 관리자에게 보여주는 배송 단계. 결제 완료 뒤 송장이 없으면 상품 준비 중이다. */
public enum DeliveryStatus {
    PREPARING, SHIPPED, DELIVERED
}
