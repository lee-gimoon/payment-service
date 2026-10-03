-- Orders left unpaid for too long are canceled by a scheduled job.
-- A canceled order holds no approval slot and never takes a new payment.
-- Stock is untouched: it is only taken at the approval gate, never by an unpaid order.
ALTER TABLE purchase_orders ADD COLUMN canceled_at TIMESTAMP WITH TIME ZONE;

ALTER TABLE purchase_orders
    DROP CONSTRAINT purchase_orders_status_check,
    DROP CONSTRAINT purchase_orders_approval_slot_check;

ALTER TABLE purchase_orders
    ADD CONSTRAINT purchase_orders_status_check
        CHECK (status IN ('PENDING_PAYMENT', 'PAYMENT_IN_PROGRESS', 'PAID', 'CANCELED')),
    ADD CONSTRAINT purchase_orders_approval_slot_check CHECK (
        (status IN ('PENDING_PAYMENT', 'CANCELED')) = (approval_attempt_id IS NULL)
    ),
    ADD CONSTRAINT purchase_orders_canceled_at_check CHECK ((status = 'CANCELED') = (canceled_at IS NOT NULL));

-- The expiry job reads unpaid orders oldest first.
CREATE INDEX purchase_orders_unpaid_created_idx ON purchase_orders (created_at) WHERE status = 'PENDING_PAYMENT';
