-- Shipping progress of a paid order, entered by shop admins.
-- A paid order without a row is still being prepared.
-- One shipment per order for now; split deliveries would drop the UNIQUE on order_id.
CREATE TABLE shipments (
    id VARCHAR(36) PRIMARY KEY,
    order_id VARCHAR(64) NOT NULL UNIQUE REFERENCES purchase_orders(id),
    status VARCHAR(16) NOT NULL CHECK (status IN ('SHIPPED', 'DELIVERED')),
    carrier VARCHAR(16) NOT NULL CHECK (carrier IN ('CJ', 'HANJIN', 'LOTTE', 'EPOST', 'LOGEN')),
    -- Digits only.
    tracking_number VARCHAR(20) NOT NULL CHECK (tracking_number ~ '^[0-9]{8,20}$'),
    shipped_at TIMESTAMP WITH TIME ZONE NOT NULL,
    delivered_at TIMESTAMP WITH TIME ZONE,
    version BIGINT NOT NULL DEFAULT 0,
    CHECK ((status = 'DELIVERED') = (delivered_at IS NOT NULL))
);

CREATE INDEX shipments_status_shipped_idx ON shipments (status, shipped_at DESC);

-- The admin order list reads paid orders by payment time.
CREATE INDEX purchase_orders_paid_idx ON purchase_orders (paid_at) WHERE status = 'PAID';
