/* 파일 역할: 결제 서비스가 PG 구현에 직접 의존하지 않도록 승인·조회 기능의 계약을 정의한다. */
package com.example.payment.gateway;

import com.example.payment.payment.PaymentOutcome;

/** 운영 코드에서는 토스 연동 구현을, 테스트에서는 모의 구현을 연결할 수 있는 PG 경계다. */
public interface PaymentGateway {
    /** 주문·결제 정보와 멱등 키로 PG에 승인을 요청하고 내부 상태로 해석한 결과를 반환한다. */
    PaymentOutcome confirm(PaymentCommand command);
    /** 기존 결제 키로 PG 결과를 조회한다. 승인을 새로 요청하지 않는다. */
    PaymentOutcome lookup(PaymentCommand command);

    /** PG 호출 정보와 결과 저장에 필요한 식별자다. attemptId는 멱등 키, operationId는 작업 소유권에 사용한다. */
    record PaymentCommand(String orderId, String paymentKey, long amount, String attemptId, String operationId) {}
}
