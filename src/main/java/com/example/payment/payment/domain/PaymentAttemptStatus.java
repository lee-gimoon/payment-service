package com.example.payment.payment.domain;

import java.util.Set;

public enum PaymentAttemptStatus {
    STARTED,
    AUTH_CANCELED,
    AUTH_FAILED,
    APPROVING,
    UNKNOWN,
    REVIEW_REQUIRED,
    SUCCEEDED,
    FAILED;

    /** 승인을 요청했지만 PG 결과가 아직 확정되지 않은 상태. */
    public static final Set<PaymentAttemptStatus> AWAITING_RESULT = Set.of(APPROVING, UNKNOWN, REVIEW_REQUIRED);

    /** 복구 작업이 PG 조회로 확정을 시도하는 상태. REVIEW_REQUIRED는 사람이 확인한다. */
    public static final Set<PaymentAttemptStatus> RECOVERABLE = Set.of(APPROVING, UNKNOWN);
}
