-- Saved shipping addresses of a customer, managed on the shop's My Page.
-- Orders will copy the chosen address, so editing or deleting one here never changes a past order.
CREATE TABLE customer_addresses (
    id VARCHAR(36) PRIMARY KEY,
    -- Keycloak user id (access token subject), the same customer id that orders use.
    customer_id VARCHAR(64) NOT NULL,
    label VARCHAR(20) NOT NULL CHECK (length(btrim(label)) > 0),
    recipient_name VARCHAR(50) NOT NULL CHECK (length(btrim(recipient_name)) > 0),
    -- Digits only, such as 01012345678 or 0212345678.
    phone VARCHAR(11) NOT NULL CHECK (phone ~ '^0[0-9]{8,10}$'),
    postal_code VARCHAR(5) NOT NULL CHECK (postal_code ~ '^[0-9]{5}$'),
    address VARCHAR(200) NOT NULL CHECK (length(btrim(address)) > 0),
    address_detail VARCHAR(100) NOT NULL,
    is_default BOOLEAN NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX customer_addresses_customer_idx ON customer_addresses (customer_id, created_at DESC);

-- A customer has at most one default address.
CREATE UNIQUE INDEX customer_addresses_one_default_idx ON customer_addresses (customer_id) WHERE is_default;
