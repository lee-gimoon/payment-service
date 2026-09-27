CREATE TABLE products (
    id VARCHAR(32) PRIMARY KEY,
    name VARCHAR(100) NOT NULL CHECK (length(btrim(name)) > 0),
    subtitle VARCHAR(100) NOT NULL,
    category VARCHAR(40) NOT NULL,
    price BIGINT NOT NULL CHECK (price > 0),
    color VARCHAR(7) NOT NULL,
    stage VARCHAR(7) NOT NULL,
    artwork VARCHAR(32) NOT NULL,
    badge VARCHAR(32) NOT NULL,
    display_order INTEGER NOT NULL UNIQUE CHECK (display_order > 0),
    active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE purchase_orders (
    id VARCHAR(64) PRIMARY KEY,
    product_name VARCHAR(100) NOT NULL,
    quantity INTEGER NOT NULL CHECK (quantity BETWEEN 1 AND 100),
    amount BIGINT NOT NULL CHECK (amount > 0),
    currency VARCHAR(3) NOT NULL CHECK (currency = 'KRW'),
    status VARCHAR(24) NOT NULL CHECK (status IN ('PENDING_PAYMENT', 'CONFIRMED')),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);

-- Order lines retain the purchased price and name after catalog changes.
CREATE TABLE purchase_order_items (
    id VARCHAR(36) PRIMARY KEY,
    order_id VARCHAR(64) NOT NULL REFERENCES purchase_orders(id),
    line_number INTEGER NOT NULL CHECK (line_number >= 0),
    product_id VARCHAR(32) NOT NULL REFERENCES products(id),
    product_name VARCHAR(100) NOT NULL,
    size VARCHAR(4) NOT NULL CHECK (size IN ('S', 'M', 'L', 'XL')),
    unit_price BIGINT NOT NULL CHECK (unit_price > 0),
    quantity INTEGER NOT NULL CHECK (quantity BETWEEN 1 AND 10),
    UNIQUE (order_id, line_number),
    UNIQUE (order_id, product_id, size)
);

CREATE TABLE payment_attempts (
    id VARCHAR(36) PRIMARY KEY,
    order_id VARCHAR(64) NOT NULL REFERENCES purchase_orders(id),
    status VARCHAR(24) NOT NULL CHECK (status IN (
        'STARTED', 'AUTH_CANCELED', 'AUTH_FAILED', 'PROCESSING',
        'SUCCEEDED', 'FAILED', 'REVIEW_REQUIRED'
    )),
    started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    finished_at TIMESTAMP WITH TIME ZONE,
    error_code VARCHAR(80),
    version BIGINT NOT NULL DEFAULT 0,
    UNIQUE (id, order_id)
);

CREATE INDEX payment_attempts_order_started_idx ON payment_attempts (order_id, started_at DESC);

CREATE TABLE payments (
    id VARCHAR(36) PRIMARY KEY,
    order_id VARCHAR(64) NOT NULL REFERENCES purchase_orders(id),
    attempt_id VARCHAR(36) NOT NULL UNIQUE,
    payment_key VARCHAR(200) NOT NULL UNIQUE,
    requested_amount BIGINT NOT NULL CHECK (requested_amount > 0),
    requested_currency VARCHAR(3) NOT NULL CHECK (requested_currency = 'KRW'),
    status VARCHAR(20) NOT NULL CHECK (status IN (
        'PROCESSING', 'SUCCEEDED', 'FAILED', 'UNKNOWN', 'REVIEW_REQUIRED'
    )),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    checked_at TIMESTAMP WITH TIME ZONE,
    approved_at TIMESTAMP WITH TIME ZONE,
    pg_amount NUMERIC(24, 6),
    pg_currency VARCHAR(3),
    pg_status VARCHAR(40),
    error_code VARCHAR(80),
    version BIGINT NOT NULL DEFAULT 0,
    FOREIGN KEY (attempt_id, order_id) REFERENCES payment_attempts(id, order_id),
    CHECK (status <> 'SUCCEEDED' OR (
        pg_status IS NOT NULL AND pg_status = 'DONE'
        AND approved_at IS NOT NULL AND checked_at IS NOT NULL
        AND pg_amount IS NOT NULL AND pg_amount = requested_amount
        AND pg_currency IS NOT NULL AND pg_currency = requested_currency
    ))
);

CREATE INDEX payments_order_created_idx ON payments (order_id, created_at DESC, id DESC);

-- Uncertain or successful transactions block another approval; failures remain retryable.
CREATE UNIQUE INDEX payments_one_live_per_order_idx ON payments (order_id) WHERE status <> 'FAILED';

INSERT INTO products (id, name, subtitle, category, price, color, stage, artwork, badge, display_order) VALUES
    ('tee-01', '선데이 크루 티', 'IVORY / REGULAR', '베이식', 19000, '#fff5de', '#f9e9d9', 'sun', 'BEST', 1),
    ('tee-02', '볼트 그래픽 티', 'BLACK / OVERSIZE', '그래픽', 28000, '#34364a', '#e3e2ee', 'bolt', 'NEW', 2),
    ('tee-03', '라일락 스트라이프', 'LILAC / RELAXED', '스트라이프', 25000, '#d8cafa', '#eee7fa', 'stripe', '', 3),
    ('tee-04', '블루 스타 티', 'BLUE / BOXY', '그래픽', 27000, '#4e78d7', '#dbe9fa', 'star', '', 4),
    ('tee-05', '민트 포켓 티', 'MINT / REGULAR', '베이식', 23000, '#a2ddc5', '#dff4e9', 'pocket', '', 5),
    ('tee-06', '피치 체크 티', 'PEACH / RELAXED', '스트라이프', 26000, '#ffa98e', '#fbe7df', 'check', 'NEW', 6),
    ('tee-07', '텐 오렌지 티', 'ORANGE / BOXY', '그래픽', 29000, '#f3a44a', '#faeddb', 'ten', '', 7),
    ('tee-08', '미드나잇 문 티', 'CHARCOAL / HEAVY', '그래픽', 31000, '#515469', '#e2e3eb', 'moon', '', 8),
    ('tee-09', '스마일 옐로 티', 'YELLOW / REGULAR', '그래픽', 24000, '#f5d661', '#f8f0cc', 'smile', '', 9),
    ('tee-10', '웨이브 퍼플 티', 'PURPLE / RELAXED', '그래픽', 27000, '#8d87da', '#eae6fa', 'wave', 'LIMITED', 10);
