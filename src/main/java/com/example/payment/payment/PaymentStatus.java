package com.example.payment.payment;

/** 화면에서 사용하는 결제 상태다. 카드 인증만 끝난 상태는 결제 성공이 아니다. */
public enum PaymentStatus {
    READY,       // 주문만 생성됨. 아직 결제 행이 없다.
    PROCESSING,  // 승인 요청을 저장했고 결과를 기다리는 중
    SUCCEEDED,   // 토스 승인 완료
    FAILED,      // 토스에서 거절·만료 확인
    UNKNOWN      // 통신 오류 등으로 토스 결과 재확인 필요
}
