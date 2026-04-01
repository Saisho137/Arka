-- ═══════════════════════════════════════════════════
-- Arka · db_provider · Initialization Script
-- Microservice: ms-provider (ACL Proveedores)
-- ═══════════════════════════════════════════════════

CREATE TABLE IF NOT EXISTS purchase_orders (
    id              UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    sku             VARCHAR(50)    NOT NULL,
    supplier_name   VARCHAR(200)   NOT NULL,
    supplier_email  VARCHAR(255),
    quantity        INTEGER        NOT NULL CHECK (quantity > 0),
    status          VARCHAR(30)    NOT NULL DEFAULT 'CREATED',
    created_at      TIMESTAMP      NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP      NOT NULL DEFAULT now()
);

-- Idempotencia
CREATE TABLE IF NOT EXISTS processed_events (
    event_id        UUID           PRIMARY KEY,
    processed_at    TIMESTAMP      NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_purchase_orders_sku    ON purchase_orders (sku);
CREATE INDEX IF NOT EXISTS idx_purchase_orders_status ON purchase_orders (status);
