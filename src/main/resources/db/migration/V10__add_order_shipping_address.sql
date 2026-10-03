-- The shipping address chosen at checkout, copied onto the order.
-- Editing or deleting the saved address later does not change it.
-- Orders created before checkout asked for an address keep these columns NULL.
ALTER TABLE purchase_orders
    ADD COLUMN shipping_recipient_name VARCHAR(50),
    ADD COLUMN shipping_phone VARCHAR(11) CHECK (shipping_phone ~ '^0[0-9]{8,10}$'),
    ADD COLUMN shipping_postal_code VARCHAR(5) CHECK (shipping_postal_code ~ '^[0-9]{5}$'),
    ADD COLUMN shipping_address VARCHAR(200),
    ADD COLUMN shipping_address_detail VARCHAR(100),
    ADD COLUMN shipping_memo VARCHAR(50);

-- An order has the whole address or none of it. The delivery memo is optional.
ALTER TABLE purchase_orders ADD CONSTRAINT purchase_orders_shipping_complete CHECK (
    (shipping_recipient_name IS NULL) = (shipping_phone IS NULL)
    AND (shipping_recipient_name IS NULL) = (shipping_postal_code IS NULL)
    AND (shipping_recipient_name IS NULL) = (shipping_address IS NULL)
    AND (shipping_recipient_name IS NULL) = (shipping_address_detail IS NULL)
    AND (shipping_memo IS NULL OR shipping_recipient_name IS NOT NULL)
);
