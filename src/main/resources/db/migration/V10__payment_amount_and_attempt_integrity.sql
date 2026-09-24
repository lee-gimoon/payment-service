-- 주문과 거래에 당시 요청 통화·금액을 보존한다. 현재 판매 통화는 KRW 한 가지다.
ALTER TABLE purchase_orders
    ADD COLUMN currency VARCHAR(3) NOT NULL DEFAULT 'KRW',
    ADD CONSTRAINT purchase_orders_currency_check CHECK (currency = 'KRW');

ALTER TABLE payments
    ADD COLUMN requested_amount BIGINT,
    ADD COLUMN requested_currency VARCHAR(3);
UPDATE payments p SET requested_amount = o.amount, requested_currency = o.currency
FROM purchase_orders o WHERE o.id = p.order_id;
ALTER TABLE payments
    ALTER COLUMN requested_amount SET NOT NULL,
    ALTER COLUMN requested_currency SET NOT NULL,
    ADD CONSTRAINT payments_requested_amount_check CHECK (requested_amount > 0),
    ADD CONSTRAINT payments_requested_currency_check CHECK (requested_currency = 'KRW');

-- 거래와 결제 시도가 반드시 같은 주문을 참조하도록 DB에서도 검사한다.
ALTER TABLE payment_attempts ADD CONSTRAINT payment_attempts_id_order_unique UNIQUE (id, order_id);
ALTER TABLE payments DROP CONSTRAINT payments_attempt_fk;
ALTER TABLE payments ADD CONSTRAINT payments_attempt_order_fk
    FOREIGN KEY (attempt_id, order_id) REFERENCES payment_attempts(id, order_id);
