CREATE TABLE purchase_orders (
    id VARCHAR(64) PRIMARY KEY,
    product_name VARCHAR(100) NOT NULL,
    quantity INTEGER NOT NULL CHECK (quantity = 1),
    amount BIGINT NOT NULL CHECK (amount = 10000),
    currency VARCHAR(3) NOT NULL CHECK (currency = 'KRW'),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE (id, amount)
);

CREATE TABLE payments (
    order_id VARCHAR(64) PRIMARY KEY REFERENCES purchase_orders(id),
    attempt_id VARCHAR(36) NOT NULL UNIQUE,
    payment_key VARCHAR(200) NOT NULL UNIQUE,
    amount BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('PROCESSING', 'SUCCEEDED', 'FAILED', 'UNKNOWN')),
    operation_id VARCHAR(36) NOT NULL,
    processing_until TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    checked_at TIMESTAMP WITH TIME ZONE,
    approved_at TIMESTAMP WITH TIME ZONE,
    pg_status VARCHAR(40),
    error_code VARCHAR(80),
    FOREIGN KEY (order_id, amount) REFERENCES purchase_orders(id, amount),
    CHECK (status <> 'SUCCEEDED' OR (pg_status = 'DONE' AND approved_at IS NOT NULL AND checked_at IS NOT NULL))
);

CREATE INDEX payments_recovery_idx ON payments (status, processing_until);
