-- ═══════════════════════════════════════════════════
-- Arka · db_payment · Initialization Script
-- Microservice: ms-payment (ACL Pasarelas de Pago)
-- ═══════════════════════════════════════════════════

CREATE TABLE IF NOT EXISTS payments (
    id              UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id        UUID           NOT NULL,
    amount          NUMERIC(15,2)  NOT NULL CHECK (amount >= 0),
    currency        VARCHAR(3)     NOT NULL DEFAULT 'COP',
    status          VARCHAR(30)    NOT NULL DEFAULT 'PENDING',
    payment_method  VARCHAR(50),
    gateway         VARCHAR(50),
    gateway_ref     VARCHAR(100),
    processed_at    TIMESTAMP      NOT NULL DEFAULT now()
);

-- Idempotencia
CREATE TABLE IF NOT EXISTS processed_events (
    event_id        UUID           PRIMARY KEY,
    processed_at    TIMESTAMP      NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_payments_order_id ON payments (order_id);
CREATE INDEX IF NOT EXISTS idx_payments_status   ON payments (status);
