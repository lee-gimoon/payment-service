-- 기존 주문과 결제 행은 유지하고, 더 이상 쓰지 않는 컬럼만 제거한다.
-- 결제 금액은 purchase_orders.amount를 사용하며, 원화 전용이므로 currency는 API에서 KRW로 반환한다.
ALTER TABLE payments
    DROP COLUMN attempt_id,
    DROP COLUMN operation_id,
    DROP COLUMN processing_until,
    DROP COLUMN amount,
    DROP COLUMN created_at;

ALTER TABLE purchase_orders
    DROP COLUMN currency,
    DROP CONSTRAINT purchase_orders_id_amount_key;

-- JPA가 동시에 저장된 다른 결과를 덮어쓰지 않도록 검사하는 번호다.
ALTER TABLE payments ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
