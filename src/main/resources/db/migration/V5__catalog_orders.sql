-- 한 주문에 여러 티셔츠를 담을 수 있도록 기존 고정 금액 제약을 확장한다.
ALTER TABLE purchase_orders
    DROP CONSTRAINT IF EXISTS purchase_orders_quantity_check,
    DROP CONSTRAINT IF EXISTS purchase_orders_amount_check;
ALTER TABLE purchase_orders
    ADD CONSTRAINT purchase_orders_quantity_positive CHECK (quantity BETWEEN 1 AND 100),
    ADD CONSTRAINT purchase_orders_amount_positive CHECK (amount > 0);

-- 상품명·단가·옵션을 주문 시점 값으로 보관한다. 기존 주문은 이 테이블에 행이 없어도 조회 가능하다.
CREATE TABLE purchase_order_items (
    order_id VARCHAR(64) NOT NULL REFERENCES purchase_orders(id),
    line_number INTEGER NOT NULL,
    product_id VARCHAR(32) NOT NULL,
    product_name VARCHAR(100) NOT NULL,
    size VARCHAR(4) NOT NULL,
    unit_price BIGINT NOT NULL CHECK (unit_price > 0),
    quantity INTEGER NOT NULL CHECK (quantity BETWEEN 1 AND 10),
    PRIMARY KEY (order_id, line_number)
);
