-- Keycloak user id (access token subject) of the customer who placed the order.
-- Orders created before sign-in existed keep NULL, so no customer can read or pay them.
ALTER TABLE purchase_orders ADD COLUMN customer_id VARCHAR(64);

CREATE INDEX purchase_orders_customer_created_idx ON purchase_orders (customer_id, created_at DESC);
