-- Give most tees a small, uneven stock (1-10 per size, drawn once at random) instead of 20 everywhere.
-- tee-01..03 keep 20 per size, and the LIMITED tee-10 keeps 3 per size with XL sold out.
-- Approvals already in progress hold their items (see V7), so they stay taken from the new values.
UPDATE product_stocks
SET quantity = GREATEST(0, seed.quantity - COALESCE(held.quantity, 0))
FROM (VALUES
    ('tee-04', 'S', 8), ('tee-04', 'M', 10), ('tee-04', 'L', 10), ('tee-04', 'XL', 1),
    ('tee-05', 'S', 3), ('tee-05', 'M', 4), ('tee-05', 'L', 4), ('tee-05', 'XL', 2),
    ('tee-06', 'S', 5), ('tee-06', 'M', 4), ('tee-06', 'L', 4), ('tee-06', 'XL', 1),
    ('tee-07', 'S', 5), ('tee-07', 'M', 10), ('tee-07', 'L', 7), ('tee-07', 'XL', 8),
    ('tee-08', 'S', 9), ('tee-08', 'M', 5), ('tee-08', 'L', 4), ('tee-08', 'XL', 9),
    ('tee-09', 'S', 1), ('tee-09', 'M', 3), ('tee-09', 'L', 9), ('tee-09', 'XL', 5)
) AS seed(product_id, size, quantity)
LEFT JOIN (
    SELECT items.product_id, items.size, sum(items.quantity) AS quantity
    FROM purchase_order_items items
    JOIN purchase_orders orders ON orders.id = items.order_id
    WHERE orders.status = 'PAYMENT_IN_PROGRESS'
    GROUP BY items.product_id, items.size
) held ON held.product_id = seed.product_id AND held.size = seed.size
WHERE product_stocks.product_id = seed.product_id AND product_stocks.size = seed.size;
