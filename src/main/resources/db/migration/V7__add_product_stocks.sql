-- Stock per product size. An order takes its items when it claims the approval slot
-- and returns them only when the approval is confirmed to have failed.
-- A size without a row has no stock.
CREATE TABLE product_stocks (
    product_id VARCHAR(32) NOT NULL REFERENCES products(id),
    size VARCHAR(4) NOT NULL CHECK (size IN ('S', 'M', 'L', 'XL')),
    quantity INTEGER NOT NULL CHECK (quantity >= 0),
    PRIMARY KEY (product_id, size)
);

INSERT INTO product_stocks (product_id, size, quantity)
SELECT products.id, sizes.size, CASE WHEN products.id = 'tee-10' THEN 3 ELSE 20 END
FROM products CROSS JOIN (VALUES ('S'), ('M'), ('L'), ('XL')) AS sizes(size);

-- The LIMITED tee starts sold out in XL so the sold-out state can be checked locally.
UPDATE product_stocks SET quantity = 0 WHERE product_id = 'tee-10' AND size = 'XL';

-- Approvals already in progress hold their items, so a later confirmed failure can return them.
UPDATE product_stocks
SET quantity = GREATEST(0, product_stocks.quantity - held.quantity)
FROM (
    SELECT items.product_id, items.size, sum(items.quantity) AS quantity
    FROM purchase_order_items items
    JOIN purchase_orders orders ON orders.id = items.order_id
    WHERE orders.status = 'PAYMENT_IN_PROGRESS'
    GROUP BY items.product_id, items.size
) held
WHERE product_stocks.product_id = held.product_id AND product_stocks.size = held.size;
