package com.example.payment.payment;

/** 화면에서 사용하는 결제 상태다. 결제수단 인증만 끝난 상태는 결제 성공이 아니다. */
public enum PaymentStatus {
    READY,       // 주문만 생성됨. 아직 결제 행이 없다.
    PROCESSING,  // 승인 요청을 저장했고 결과를 기다리는 중
    SUCCEEDED,   // 토스 승인 완료
    FAILED,      // 토스에서 거절·만료 확인
    UNKNOWN,     // 서버가 토스 결과를 자동 재조회하는 중
    CANCEL_PENDING, // 승인된 결제의 자동 취소를 요청했거나 결과를 확인하는 중
    CANCELED,    // 토스에서 전액 취소 완료 확인
    REVIEW_REQUIRED // 자동 처리로 확정할 수 없어 운영자 확인 필요
}
