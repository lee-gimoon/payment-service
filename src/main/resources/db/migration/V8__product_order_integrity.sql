-- 판매 중지 상품은 목록과 새 주문에서 제외하고, 과거 주문 때문에 상품 행은 유지한다.
ALTER TABLE products
    ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE,
    ADD CONSTRAINT products_name_not_blank CHECK (length(btrim(name)) > 0),
    ADD CONSTRAINT products_display_order_positive CHECK (display_order > 0);

-- 새 주문은 INSERT 대상으로 구분하고, 이후 동시 변경이 생기면 버전 충돌을 감지한다.
ALTER TABLE purchase_orders ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

-- 주문 항목은 실제 상품과 주문을 각각 참조한다. 상품 삭제 대신 active=false를 사용한다.
ALTER TABLE purchase_order_items
    ADD CONSTRAINT purchase_order_items_product_fk FOREIGN KEY (product_id) REFERENCES products(id),
    ADD CONSTRAINT purchase_order_items_line_number_check CHECK (line_number >= 0),
    ADD CONSTRAINT purchase_order_items_size_check CHECK (size IN ('S', 'M', 'L', 'XL'));
