package com.example.payment.order;

/** 다른 패키지의 도메인 테스트가 저장 없이 주문을 만들 때 쓴다. */
public final class TestOrders {
    private TestOrders() {}

    public static PurchaseOrder order(long amount) {
        return new PurchaseOrder("선데이 크루 티", 1, 1, amount);
    }
}
