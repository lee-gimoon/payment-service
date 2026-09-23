-- 진행 중인 행도 서버 재시작 후 자동으로 다시 확인한다. 기존 주문·결제 결과는 보존한다.
ALTER TABLE payments DROP CONSTRAINT payments_status_check;
ALTER TABLE payments ADD CONSTRAINT payments_status_check CHECK
    (status IN ('PROCESSING', 'SUCCEEDED', 'FAILED', 'UNKNOWN', 'CANCEL_PENDING', 'CANCELED', 'REVIEW_REQUIRED'));

ALTER TABLE payments
    ADD COLUMN pg_amount NUMERIC(24, 6),
    ADD COLUMN pg_currency VARCHAR(3),
    ADD COLUMN canceled_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN cancel_idempotency_key VARCHAR(36) UNIQUE,
    ADD COLUMN cancel_requested_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN next_action_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN recovery_attempts INTEGER NOT NULL DEFAULT 0;

UPDATE payments SET next_action_at = now() + interval '2 minutes'
WHERE status IN ('PROCESSING', 'UNKNOWN');

CREATE INDEX payments_next_action_idx ON payments (next_action_at) WHERE next_action_at IS NOT NULL;
ALTER TABLE payments ADD CONSTRAINT payments_cancel_intent_check CHECK
    (status <> 'CANCEL_PENDING' OR
        (cancel_idempotency_key IS NOT NULL AND cancel_requested_at IS NOT NULL
         AND pg_amount IS NOT NULL AND pg_amount > 0 AND pg_currency IS NOT NULL));
ALTER TABLE payments ADD CONSTRAINT payments_canceled_check CHECK
    (status <> 'CANCELED' OR (pg_status = 'CANCELED' AND canceled_at IS NOT NULL));
