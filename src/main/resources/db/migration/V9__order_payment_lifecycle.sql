-- 주문 상태와 결제 시도·거래를 분리한다. 기존 주문과 결제 행은 삭제하지 않는다.
ALTER TABLE purchase_orders
    ADD COLUMN status VARCHAR(24) NOT NULL DEFAULT 'PENDING_PAYMENT',
    ADD CONSTRAINT purchase_orders_status_check
        CHECK (status IN ('PENDING_PAYMENT', 'CONFIRMED', 'CANCELED'));

UPDATE purchase_orders o SET status = CASE
    WHEN EXISTS (SELECT 1 FROM payments p WHERE p.order_id = o.id AND p.status = 'SUCCEEDED') THEN 'CONFIRMED'
    WHEN EXISTS (SELECT 1 FROM payments p WHERE p.order_id = o.id AND p.status = 'CANCELED') THEN 'CANCELED'
    ELSE 'PENDING_PAYMENT'
END;

-- 한 주문에서 결제창을 여러 번 열 수 있다. 인증 전에는 payment_key가 없으므로 별도 행으로 남긴다.
CREATE TABLE payment_attempts (
    id VARCHAR(36) PRIMARY KEY,
    order_id VARCHAR(64) NOT NULL REFERENCES purchase_orders(id),
    status VARCHAR(24) NOT NULL CHECK (status IN
        ('STARTED', 'AUTH_CANCELED', 'AUTH_FAILED', 'PROCESSING', 'SUCCEEDED',
         'FAILED', 'CANCELED', 'REVIEW_REQUIRED')),
    started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    finished_at TIMESTAMP WITH TIME ZONE,
    error_code VARCHAR(80),
    version BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX payment_attempts_order_started_idx ON payment_attempts (order_id, started_at DESC);

-- 결제 거래도 주문당 여러 건을 저장한다. 실패 건은 보존하고 같은 주문으로 재시도할 수 있다.
ALTER TABLE payments ADD COLUMN id VARCHAR(36);
UPDATE payments SET id = gen_random_uuid()::text;
ALTER TABLE payments ALTER COLUMN id SET NOT NULL;
ALTER TABLE payments DROP CONSTRAINT payments_pkey;
ALTER TABLE payments ADD CONSTRAINT payments_pkey PRIMARY KEY (id);
ALTER TABLE payments ADD COLUMN created_at TIMESTAMP WITH TIME ZONE;
UPDATE payments p SET created_at = COALESCE(p.checked_at, o.created_at)
FROM purchase_orders o WHERE o.id = p.order_id;
ALTER TABLE payments ALTER COLUMN created_at SET NOT NULL;

INSERT INTO payment_attempts (id, order_id, status, started_at, finished_at, error_code)
SELECT p.id, p.order_id,
    CASE WHEN p.status IN ('PROCESSING', 'UNKNOWN', 'CANCEL_PENDING') THEN 'REVIEW_REQUIRED'
         ELSE p.status END,
    p.created_at, p.checked_at, p.error_code
FROM payments p;

ALTER TABLE payments ADD COLUMN attempt_id VARCHAR(36);
UPDATE payments SET attempt_id = id;
ALTER TABLE payments ALTER COLUMN attempt_id SET NOT NULL;
ALTER TABLE payments ADD CONSTRAINT payments_attempt_fk
    FOREIGN KEY (attempt_id) REFERENCES payment_attempts(id);
ALTER TABLE payments ADD CONSTRAINT payments_attempt_unique UNIQUE (attempt_id);
CREATE INDEX payments_order_created_idx ON payments (order_id, created_at DESC);

-- 승인 중이거나 완료·확인 필요인 거래가 있으면 다른 결제키의 승인을 시작하지 않는다.
CREATE UNIQUE INDEX payments_one_live_per_order_idx ON payments (order_id)
    WHERE status <> 'FAILED';
