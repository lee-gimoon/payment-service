-- Unlike the V1 table dropped in V2, this table holds only approvals confirmed as successful.
-- Attempts keep every approval state; a payment row is the record that money actually moved.
CREATE TABLE payments (
    id VARCHAR(36) PRIMARY KEY,
    order_id VARCHAR(64) NOT NULL REFERENCES purchase_orders(id),
    attempt_id VARCHAR(36) NOT NULL,
    payment_key VARCHAR(200) NOT NULL,
    amount BIGINT NOT NULL CHECK (amount > 0),
    currency VARCHAR(3) NOT NULL CHECK (currency = 'KRW'),
    approved_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    -- An order can be paid at most once.
    CONSTRAINT payments_order_id_key UNIQUE (order_id),
    CONSTRAINT payments_attempt_id_key UNIQUE (attempt_id),
    CONSTRAINT payments_payment_key_key UNIQUE (payment_key),
    FOREIGN KEY (attempt_id, order_id) REFERENCES payment_attempts (id, order_id)
);

INSERT INTO payments (id, order_id, attempt_id, payment_key, amount, currency, approved_at, created_at)
SELECT gen_random_uuid()::text, a.order_id, a.id, a.payment_key, a.amount, a.currency,
       a.pg_approved_at, COALESCE(a.finished_at, a.last_checked_at)
FROM payment_attempts a
WHERE a.status = 'SUCCEEDED';
