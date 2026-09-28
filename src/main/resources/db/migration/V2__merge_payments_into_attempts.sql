-- A payment attempt now carries its own approval, so the order holds at most one live approval slot.

ALTER TABLE payment_attempts DROP CONSTRAINT payment_attempts_status_check;

ALTER TABLE payment_attempts
    ADD COLUMN amount BIGINT,
    ADD COLUMN currency VARCHAR(3),
    ADD COLUMN payment_key VARCHAR(200),
    ADD COLUMN approval_requested_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN last_checked_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN pg_status VARCHAR(40),
    ADD COLUMN pg_approved_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN pg_amount NUMERIC(24, 6),
    ADD COLUMN pg_currency VARCHAR(3);

UPDATE payment_attempts a
SET amount = o.amount, currency = o.currency
FROM purchase_orders o
WHERE o.id = a.order_id;

UPDATE payment_attempts a
SET payment_key = p.payment_key,
    approval_requested_at = p.created_at,
    last_checked_at = p.checked_at,
    pg_status = p.pg_status,
    pg_approved_at = p.approved_at,
    pg_amount = p.pg_amount,
    pg_currency = p.pg_currency,
    error_code = COALESCE(p.error_code, a.error_code),
    status = CASE p.status WHEN 'PROCESSING' THEN 'APPROVING' ELSE p.status END
FROM payments p
WHERE p.attempt_id = a.id;

ALTER TABLE payment_attempts
    ALTER COLUMN amount SET NOT NULL,
    ALTER COLUMN currency SET NOT NULL,
    ADD CONSTRAINT payment_attempts_status_check CHECK (status IN (
        'STARTED', 'AUTH_CANCELED', 'AUTH_FAILED', 'APPROVING',
        'UNKNOWN', 'REVIEW_REQUIRED', 'SUCCEEDED', 'FAILED'
    )),
    ADD CONSTRAINT payment_attempts_amount_check CHECK (amount > 0),
    ADD CONSTRAINT payment_attempts_currency_check CHECK (currency = 'KRW'),
    ADD CONSTRAINT payment_attempts_payment_key_key UNIQUE (payment_key),
    -- Only attempts that reached the approval gate have a payment key.
    ADD CONSTRAINT payment_attempts_payment_key_check CHECK (
        (status IN ('STARTED', 'AUTH_CANCELED', 'AUTH_FAILED')) = (payment_key IS NULL)
    ),
    ADD CONSTRAINT payment_attempts_approval_time_check CHECK (
        (payment_key IS NULL) = (approval_requested_at IS NULL)
    ),
    ADD CONSTRAINT payment_attempts_success_evidence_check CHECK (status <> 'SUCCEEDED' OR (
        pg_status IS NOT NULL AND pg_status = 'DONE'
        AND pg_approved_at IS NOT NULL AND last_checked_at IS NOT NULL
        AND pg_amount IS NOT NULL AND pg_amount = amount
        AND pg_currency IS NOT NULL AND pg_currency = currency
    ));

-- Backstop for the order slot: an order never has two live or successful approvals.
CREATE UNIQUE INDEX payment_attempts_one_live_per_order_idx ON payment_attempts (order_id)
    WHERE status IN ('APPROVING', 'UNKNOWN', 'REVIEW_REQUIRED', 'SUCCEEDED');

CREATE INDEX payment_attempts_order_approval_idx ON payment_attempts (order_id, approval_requested_at DESC)
    WHERE approval_requested_at IS NOT NULL;

CREATE INDEX payment_attempts_unresolved_idx ON payment_attempts (approval_requested_at)
    WHERE status IN ('APPROVING', 'UNKNOWN');

ALTER TABLE purchase_orders DROP CONSTRAINT purchase_orders_status_check;

ALTER TABLE purchase_orders
    ADD COLUMN approval_attempt_id VARCHAR(36),
    ADD COLUMN paid_at TIMESTAMP WITH TIME ZONE;

UPDATE purchase_orders o
SET status = 'PAID',
    approval_attempt_id = a.id,
    paid_at = COALESCE(a.finished_at, a.last_checked_at, a.approval_requested_at)
FROM payment_attempts a
WHERE a.order_id = o.id AND a.status = 'SUCCEEDED';

UPDATE purchase_orders o
SET status = 'PAYMENT_IN_PROGRESS', approval_attempt_id = a.id
FROM payment_attempts a
WHERE a.order_id = o.id AND a.status IN ('APPROVING', 'UNKNOWN', 'REVIEW_REQUIRED');

ALTER TABLE purchase_orders
    ADD CONSTRAINT purchase_orders_status_check CHECK (status IN ('PENDING_PAYMENT', 'PAYMENT_IN_PROGRESS', 'PAID')),
    ADD CONSTRAINT purchase_orders_approval_slot_check CHECK (
        (status = 'PENDING_PAYMENT') = (approval_attempt_id IS NULL)
    ),
    ADD CONSTRAINT purchase_orders_paid_at_check CHECK ((status = 'PAID') = (paid_at IS NOT NULL)),
    ADD CONSTRAINT purchase_orders_approval_attempt_fk FOREIGN KEY (approval_attempt_id, id)
        REFERENCES payment_attempts (id, order_id);

DROP TABLE payments;
