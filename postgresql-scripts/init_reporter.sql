-- ═══════════════════════════════════════════════════
-- Arka · db_reporter · Initialization Script
-- Microservice: ms-reporter (CQRS / Event Sourcing)
-- ═══════════════════════════════════════════════════

-- ───────────────────────────────────────────────────
-- Event Store: almacena todos los eventos del ecosistema (particionada por fecha)
-- ───────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS event_store (
    id              UUID           DEFAULT gen_random_uuid(),
    event_id        UUID           NOT NULL,
    event_type      VARCHAR(100)   NOT NULL,
    source          VARCHAR(50)    NOT NULL,
    aggregate_id    VARCHAR(50)    NOT NULL,
    correlation_id  UUID,
    payload         JSONB          NOT NULL,
    timestamp       TIMESTAMP      NOT NULL DEFAULT now(),
    PRIMARY KEY (id, timestamp)
) PARTITION BY RANGE (timestamp);

-- Particiones mensuales 2026
CREATE TABLE IF NOT EXISTS event_store_2026_01 PARTITION OF event_store FOR VALUES FROM ('2026-01-01') TO ('2026-02-01');
CREATE TABLE IF NOT EXISTS event_store_2026_02 PARTITION OF event_store FOR VALUES FROM ('2026-02-01') TO ('2026-03-01');
CREATE TABLE IF NOT EXISTS event_store_2026_03 PARTITION OF event_store FOR VALUES FROM ('2026-03-01') TO ('2026-04-01');
CREATE TABLE IF NOT EXISTS event_store_2026_04 PARTITION OF event_store FOR VALUES FROM ('2026-04-01') TO ('2026-05-01');
CREATE TABLE IF NOT EXISTS event_store_2026_05 PARTITION OF event_store FOR VALUES FROM ('2026-05-01') TO ('2026-06-01');
CREATE TABLE IF NOT EXISTS event_store_2026_06 PARTITION OF event_store FOR VALUES FROM ('2026-06-01') TO ('2026-07-01');
CREATE TABLE IF NOT EXISTS event_store_2026_07 PARTITION OF event_store FOR VALUES FROM ('2026-07-01') TO ('2026-08-01');
CREATE TABLE IF NOT EXISTS event_store_2026_08 PARTITION OF event_store FOR VALUES FROM ('2026-08-01') TO ('2026-09-01');
CREATE TABLE IF NOT EXISTS event_store_2026_09 PARTITION OF event_store FOR VALUES FROM ('2026-09-01') TO ('2026-10-01');
CREATE TABLE IF NOT EXISTS event_store_2026_10 PARTITION OF event_store FOR VALUES FROM ('2026-10-01') TO ('2026-11-01');
CREATE TABLE IF NOT EXISTS event_store_2026_11 PARTITION OF event_store FOR VALUES FROM ('2026-11-01') TO ('2026-12-01');
CREATE TABLE IF NOT EXISTS event_store_2026_12 PARTITION OF event_store FOR VALUES FROM ('2026-12-01') TO ('2027-01-01');

CREATE UNIQUE INDEX IF NOT EXISTS idx_event_store_event_id   ON event_store (event_id, timestamp);
CREATE INDEX IF NOT EXISTS idx_event_store_type_ts           ON event_store (event_type, timestamp);
CREATE INDEX IF NOT EXISTS idx_event_store_correlation       ON event_store (correlation_id);
CREATE INDEX IF NOT EXISTS idx_event_store_source            ON event_store (source);
CREATE INDEX IF NOT EXISTS idx_event_store_aggregate         ON event_store (aggregate_id);
CREATE INDEX IF NOT EXISTS idx_event_store_payload           ON event_store USING GIN (payload);

-- ───────────────────────────────────────────────────
-- Read Model: resumen semanal de ventas por SKU
-- ───────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS sales_summary (
    sku                 VARCHAR(50)    NOT NULL,
    week_start_date     DATE           NOT NULL,
    total_orders        INTEGER        NOT NULL DEFAULT 0,
    total_quantity      INTEGER        NOT NULL DEFAULT 0,
    total_revenue       NUMERIC(15,2)  NOT NULL DEFAULT 0,
    average_order_value NUMERIC(15,2)  NOT NULL DEFAULT 0,
    product_name        VARCHAR(255),
    last_updated_at     TIMESTAMP      NOT NULL DEFAULT now(),
    PRIMARY KEY (sku, week_start_date)
);

CREATE INDEX IF NOT EXISTS idx_sales_summary_week ON sales_summary (week_start_date);

-- ───────────────────────────────────────────────────
-- Report Metadata: estado de generación de reportes
-- ───────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS report_metadata (
    id              UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    report_type     VARCHAR(30)    NOT NULL,
    status          VARCHAR(20)    NOT NULL DEFAULT 'PROCESSING',
    start_date      DATE           NOT NULL,
    end_date        DATE           NOT NULL,
    s3_key          VARCHAR(500),
    file_size_bytes BIGINT,
    requested_by    VARCHAR(200),
    requested_at    TIMESTAMP      NOT NULL DEFAULT now(),
    completed_at    TIMESTAMP,
    error_message   TEXT
);

CREATE INDEX IF NOT EXISTS idx_report_metadata_status ON report_metadata (status);

-- ───────────────────────────────────────────────────
-- Stock Alerts: alertas de reabastecimiento
-- ───────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS stock_alerts (
    id              UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    sku             VARCHAR(50)    NOT NULL,
    product_name    VARCHAR(255),
    current_stock   INTEGER        NOT NULL,
    daily_rate      NUMERIC(10,2)  NOT NULL,
    days_until_out  INTEGER        NOT NULL,
    alert_status    VARCHAR(20)    NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMP      NOT NULL DEFAULT now(),
    resolved_at     TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_stock_alerts_sku    ON stock_alerts (sku);
CREATE INDEX IF NOT EXISTS idx_stock_alerts_status ON stock_alerts (alert_status);

-- ───────────────────────────────────────────────────
-- Rebuild Jobs: tracking de reconstrucción de Read Models
-- ───────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS rebuild_jobs (
    id              UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    read_model      VARCHAR(50)    NOT NULL,
    status          VARCHAR(20)    NOT NULL DEFAULT 'IN_PROGRESS',
    events_processed BIGINT        NOT NULL DEFAULT 0,
    started_at      TIMESTAMP      NOT NULL DEFAULT now(),
    completed_at    TIMESTAMP,
    error_message   TEXT
);

CREATE INDEX IF NOT EXISTS idx_rebuild_jobs_status ON rebuild_jobs (status);

-- ───────────────────────────────────────────────────
-- Idempotencia: eventos ya procesados
-- ───────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS processed_events (
    event_id        UUID           PRIMARY KEY,
    processed_at    TIMESTAMP      NOT NULL DEFAULT now()
);

-- ───────────────────────────────────────────────────
-- Read Model: vista materializada de órdenes para reportes
-- ───────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS report_orders (
    order_id        UUID           PRIMARY KEY,
    customer_id     VARCHAR(100)   NOT NULL,
    status          VARCHAR(30)    NOT NULL,
    total_amount    NUMERIC(15,2)  NOT NULL DEFAULT 0,
    created_at      TIMESTAMP,
    updated_at      TIMESTAMP      NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_report_orders_status   ON report_orders (status);
CREATE INDEX IF NOT EXISTS idx_report_orders_customer ON report_orders (customer_id);

-- ───────────────────────────────────────────────────
-- Read Model: vista materializada de inventario
-- ───────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS report_inventory (
    sku             VARCHAR(50)    PRIMARY KEY,
    current_stock   INTEGER        NOT NULL DEFAULT 0,
    total_reserved  INTEGER        NOT NULL DEFAULT 0,
    product_name    VARCHAR(255),
    last_updated    TIMESTAMP      NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_report_inventory_stock ON report_inventory (current_stock);
