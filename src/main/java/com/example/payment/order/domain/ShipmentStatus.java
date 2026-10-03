package com.example.payment.order.domain;

/** 송장을 등록한 뒤의 배송 상태. 송장을 등록하기 전 결제 완료 주문은 배송 행이 없다. */
public enum ShipmentStatus {
    SHIPPED, DELIVERED
}
