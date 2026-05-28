-- ═══════════════════════════════════════════════════
-- Arka · db_payment · Initialization Script
-- Microservice: ms-payment (ACL Pasarelas de Pago)
-- ═══════════════════════════════════════════════════

CREATE TABLE IF NOT EXISTS payments (
    id              UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id        UUID           NOT NULL,
    gateway         VARCHAR(50)    NOT NULL DEFAULT 'MOCK',
    transaction_id  VARCHAR(100),
    amount          NUMERIC(15,2)  NOT NULL CHECK (amount >= 0),
    currency        VARCHAR(3)     NOT NULL DEFAULT 'COP',
    status          VARCHAR(30)    NOT NULL DEFAULT 'PENDING',
    failure_reason  TEXT,
    created_at      TIMESTAMP      NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP      NOT NULL DEFAULT now()
);

-- Outbox Pattern
CREATE TABLE IF NOT EXISTS outbox_events (
    id              UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    event_type      VARCHAR(50)    NOT NULL,
    payload         TEXT           NOT NULL,
    partition_key   VARCHAR(100)   NOT NULL,
    published       BOOLEAN        NOT NULL DEFAULT false,
    created_at      TIMESTAMP      NOT NULL DEFAULT now()
);

-- Idempotencia
CREATE TABLE IF NOT EXISTS processed_events (
    id              UUID           PRIMARY KEY,
    processed_at    TIMESTAMP      NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_payments_order_id ON payments (order_id);
CREATE INDEX IF NOT EXISTS idx_payments_status   ON payments (status);
CREATE INDEX IF NOT EXISTS idx_outbox_pending    ON outbox_events (published, created_at) WHERE published = false;
