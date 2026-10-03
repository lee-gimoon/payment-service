package com.example.payment.order.api;

/**
 * @param preparing 결제는 끝났지만 송장을 등록하지 않은 주문 수
 * @param shipping 배송 중인 주문 수
 */
public record AdminOrderCounts(long preparing, long shipping) {
}
