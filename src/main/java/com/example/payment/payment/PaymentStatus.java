/* 파일 역할: 서버와 클라이언트가 결제 진행 상황을 구분하는 상태 값들을 정의한다. */
package com.example.payment.payment;

/** 결제의 업무 상태다. 토스 원본 상태인 DONE 등은 별도 pgStatus 필드에 보관한다. */
public enum PaymentStatus {
    /** 주문만 있고 승인 시도가 없을 때 응답에 표시한다. payments 테이블에는 READY 행을 저장하지 않는다. */
    READY,
    /** 승인 또는 PG 결과 재확인 작업을 진행 중이다. */
    PROCESSING,
    /** 일치하는 결제 정보와 PG 승인 완료를 확인했다. */
    SUCCEEDED,
    /** 명시적인 거절·만료 등으로 실패를 확정했다. */
    FAILED,
    /** 통신 오류나 처리 기한 초과 등으로 결과를 확정할 수 없어 PG 조회가 필요하다. */
    UNKNOWN
}
