-- ═══════════════════════════════════════════════════
-- Arka · db_shipping · Initialization Script
-- Microservice: ms-shipping (ACL Logística)
-- ═══════════════════════════════════════════════════

CREATE TABLE IF NOT EXISTS shipments (
    id                 UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id           UUID           NOT NULL,
    carrier            VARCHAR(50)    NOT NULL,
    tracking_number    VARCHAR(100),
    status             VARCHAR(30)    NOT NULL DEFAULT 'PENDING',
    estimated_delivery TIMESTAMP,
    created_at         TIMESTAMP      NOT NULL DEFAULT now(),
    updated_at         TIMESTAMP      NOT NULL DEFAULT now()
);

-- Idempotencia
CREATE TABLE IF NOT EXISTS processed_events (
    event_id        UUID           PRIMARY KEY,
    processed_at    TIMESTAMP      NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_shipments_order_id ON shipments (order_id);
CREATE INDEX IF NOT EXISTS idx_shipments_status   ON shipments (status);
CREATE INDEX IF NOT EXISTS idx_shipments_carrier  ON shipments (carrier);
