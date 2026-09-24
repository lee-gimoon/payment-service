-- 기존 주문 항목 행을 유지하면서 각 항목에 독립 식별자를 부여한다.
ALTER TABLE purchase_order_items ADD COLUMN id VARCHAR(36);
UPDATE purchase_order_items SET id = gen_random_uuid()::text;
ALTER TABLE purchase_order_items ALTER COLUMN id SET NOT NULL;

-- OrderItem 엔티티의 @Id는 id이고, 같은 주문 안의 목록 순서도 유일해야 한다.
ALTER TABLE purchase_order_items DROP CONSTRAINT purchase_order_items_pkey;
ALTER TABLE purchase_order_items ADD CONSTRAINT purchase_order_items_pkey PRIMARY KEY (id);
ALTER TABLE purchase_order_items ADD CONSTRAINT purchase_order_items_order_line_unique UNIQUE (order_id, line_number);
