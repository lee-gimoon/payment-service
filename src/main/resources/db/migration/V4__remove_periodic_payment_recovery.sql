-- 승인 요청 안에서 재조회와 조건부 취소를 처리하므로 주기적 복구 일정은 더 이상 사용하지 않는다.
DROP INDEX IF EXISTS payments_next_action_idx;
ALTER TABLE payments
    DROP COLUMN next_action_at,
    DROP COLUMN recovery_attempts;

-- 이전 버전에서 처리 중이던 행은 자동 완료를 약속하지 않고 운영 확인 대상으로 남긴다.
UPDATE payments SET status = 'REVIEW_REQUIRED', error_code = 'PG_RECONCILIATION_REQUIRED'
WHERE status IN ('PROCESSING', 'UNKNOWN', 'CANCEL_PENDING');
