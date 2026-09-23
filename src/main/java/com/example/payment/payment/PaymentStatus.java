package com.example.payment.payment;

/** 화면에서 사용하는 결제 상태다. 결제수단 인증만 끝난 상태는 결제 성공이 아니다. */
public enum PaymentStatus {
    READY,       // 주문만 생성됨. 아직 결제 행이 없다.
    PROCESSING,  // 승인 요청을 저장했고 결과를 기다리는 중
    SUCCEEDED,   // 토스 승인 완료
    FAILED,      // 토스에서 거절·만료 확인
    UNKNOWN,     // 승인 결과가 불확실해 같은 요청에서 토스 재조회가 필요한 상태
    CANCEL_PENDING, // 취소 의도와 멱등키를 저장했으며 취소 결과를 확인하는 중
    CANCELED,    // 토스에서 전액 취소 완료 확인
    REVIEW_REQUIRED // 서버에서 결과를 확정할 수 없어 운영자 확인 필요
}
