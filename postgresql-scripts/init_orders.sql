-- ═══════════════════════════════════════════════════
-- Arka · db_orders · Initialization Script
-- Microservice: ms-order (Orquestador Saga Secuencial)
-- ═══════════════════════════════════════════════════

-- ─── Tipos ENUM ───────────────────────────────────
-- Sincronizados con Java (case-sensitive):
--   OrderStatus sealed interface  → order_status
--   EventType enum                → order_event_type
--   OutboxStatus enum             → outbox_status
-- §13: Requiere EnumCodec en R2DBC + WritingConverter con EnumWriteSupport.

DO $$ BEGIN
    CREATE TYPE order_status AS ENUM (
        'PENDIENTE_RESERVA',
        'CONFIRMADO',
        'EN_DESPACHO',
        'ENTREGADO',
        'CANCELADO'
    );
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;

DO $$ BEGIN
    CREATE TYPE order_event_type AS ENUM (
        'ORDER_CREATED',
        'ORDER_CONFIRMED',
        'ORDER_STATUS_CHANGED',
        'ORDER_CANCELLED'
    );
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;

DO $$ BEGIN
    CREATE TYPE outbox_status AS ENUM (
        'PENDING',
        'PUBLISHED'
    );
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;

-- ─── Tablas ───────────────────────────────────────

CREATE TABLE IF NOT EXISTS orders (
    id               UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id      UUID          NOT NULL,
    customer_email   VARCHAR(255)  NOT NULL,
    shipping_address TEXT          NOT NULL,
    notes            TEXT,
    status           order_status  NOT NULL DEFAULT 'PENDIENTE_RESERVA',
    total_amount     DECIMAL(12,2) NOT NULL CHECK (total_amount >= 0),
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS order_items (
    id           UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id     UUID          NOT NULL REFERENCES orders(id),
    product_id   UUID          NOT NULL,
    -- SKU: identificador semántico de negocio (ej: KB-MECH-001).
    -- Texto legible usado en facturas, bodegas y comunicación con proveedores.
    sku          VARCHAR(100)  NOT NULL,
    product_name VARCHAR(255),
    quantity     INTEGER       NOT NULL CHECK (quantity > 0),
    unit_price   DECIMAL(12,2) NOT NULL,
    -- subtotal generado por PostgreSQL = quantity * unit_price.
    -- No incluir en INSERT — columna calculada automáticamente.
    subtotal     DECIMAL(12,2) GENERATED ALWAYS AS (quantity * unit_price) STORED
);

-- Auditoría de transiciones de estado.
-- previous_status es nullable: las transiciones automáticas del sistema
-- (ej. cancelación por stock insuficiente) pueden registrarse sin usuario.
-- changed_by es nullable: mismo motivo.
CREATE TABLE IF NOT EXISTS order_state_history (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id        UUID         NOT NULL REFERENCES orders(id),
    previous_status order_status,
    new_status      order_status NOT NULL,
    changed_by      UUID,
    reason          TEXT,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Outbox Pattern: eventos pendientes de publicar a Kafka.
-- partition_key = order_id → garantiza orden causal por agregado (doc §03).
-- topic incluido como columna (ms-order siempre publica a 'order-events');
-- facilita extensión futura sin cambio de estructura.
CREATE TABLE IF NOT EXISTS outbox_events (
    id            UUID             PRIMARY KEY DEFAULT gen_random_uuid(),
    event_type    order_event_type NOT NULL,
    topic         VARCHAR(100)     NOT NULL DEFAULT 'order-events',
    partition_key VARCHAR(100)     NOT NULL,
    payload       JSONB            NOT NULL,
    status        outbox_status    NOT NULL DEFAULT 'PENDING',
    created_at    TIMESTAMPTZ      NOT NULL DEFAULT now()
);

-- Idempotencia: eventos Kafka ya procesados (protección at-least-once).
-- event_id viene de Kafka → INSERT con DatabaseClient explícito (no save()) — §2.2.
CREATE TABLE IF NOT EXISTS processed_events (
    event_id     UUID        PRIMARY KEY,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ─── Índices ──────────────────────────────────────

CREATE INDEX IF NOT EXISTS idx_orders_customer_id     ON orders (customer_id);
CREATE INDEX IF NOT EXISTS idx_orders_status          ON orders (status);
CREATE INDEX IF NOT EXISTS idx_orders_created_at      ON orders (created_at DESC);
CREATE INDEX IF NOT EXISTS idx_orders_customer_status ON orders (customer_id, status);

CREATE INDEX IF NOT EXISTS idx_order_items_order_id   ON order_items (order_id);

CREATE INDEX IF NOT EXISTS idx_state_history_order_id ON order_state_history (order_id);

CREATE INDEX IF NOT EXISTS idx_outbox_status_created  ON outbox_events (status, created_at);
