-- ═══════════════════════════════════════════════════
-- Arka · db_reporter · Initialization Script
-- Microservice: ms-reporter (CQRS / Event Sourcing)
-- ═══════════════════════════════════════════════════

-- Event Store: almacena todos los eventos del ecosistema
CREATE TABLE IF NOT EXISTS event_store (
    id              UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id        UUID           NOT NULL UNIQUE,
    event_type      VARCHAR(100)   NOT NULL,
    source          VARCHAR(50)    NOT NULL,
    -- aggregate_id como texto: soporta tanto UUIDs (order_id) como SKUs (texto semántico)
    aggregate_id    VARCHAR(50)    NOT NULL,
    correlation_id  UUID,
    payload         JSONB          NOT NULL,
    timestamp       TIMESTAMP      NOT NULL DEFAULT now()
);

-- Read Model: vista materializada de órdenes para reportes
CREATE TABLE IF NOT EXISTS report_orders (
    order_id        UUID           PRIMARY KEY,
    customer_id     VARCHAR(100)   NOT NULL,
    status          VARCHAR(30)    NOT NULL,
    total_amount    NUMERIC(15,2)  NOT NULL DEFAULT 0,
    created_at      TIMESTAMP,
    updated_at      TIMESTAMP      NOT NULL DEFAULT now()
);

-- Read Model: vista materializada de inventario
CREATE TABLE IF NOT EXISTS report_inventory (
    sku             VARCHAR(50)    PRIMARY KEY,
    current_stock   INTEGER        NOT NULL DEFAULT 0,
    total_reserved  INTEGER        NOT NULL DEFAULT 0,
    last_updated    TIMESTAMP      NOT NULL DEFAULT now()
);

-- Idempotencia
CREATE TABLE IF NOT EXISTS processed_events (
    event_id        UUID           PRIMARY KEY,
    processed_at    TIMESTAMP      NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_event_store_type       ON event_store (event_type);
CREATE INDEX IF NOT EXISTS idx_event_store_source     ON event_store (source);
CREATE INDEX IF NOT EXISTS idx_event_store_aggregate  ON event_store (aggregate_id);
CREATE INDEX IF NOT EXISTS idx_event_store_payload    ON event_store USING GIN (payload);
CREATE INDEX IF NOT EXISTS idx_report_orders_status   ON report_orders (status);
CREATE INDEX IF NOT EXISTS idx_report_orders_customer ON report_orders (customer_id);
