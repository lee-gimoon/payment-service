/* 파일 역할: PG의 승인·조회 응답을 내부 결제 상태로 해석한 결과를 전달한다. */
package com.example.payment.payment;

import java.time.Instant;

/** PG 연동 계층에서 서비스로 넘기는 불변 처리 결과다. 엔티티와 분리하여 네트워크 결과만 담는다. */
public record PaymentOutcome(PaymentStatus status, String pgStatus, String errorCode, Instant approvedAt) {
    /** 결제를 성공·실패로 확정할 수 없을 때 원인 코드와 함께 UNKNOWN 결과를 만든다. */
    public static PaymentOutcome unknown(String code) {
        return new PaymentOutcome(PaymentStatus.UNKNOWN, null, code, null);
    }

    /** 명시적인 카드 거절처럼 확정된 실패를 원인 코드와 함께 표현한다. */
    public static PaymentOutcome failed(String code) {
        return new PaymentOutcome(PaymentStatus.FAILED, null, code, null);
    }
}
