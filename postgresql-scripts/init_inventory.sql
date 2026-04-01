-- ═══════════════════════════════════════════════════
-- Arka · db_inventory · Initialization Script
-- Microservice: ms-inventory (Stock, Reservas, Locks)
-- ═══════════════════════════════════════════════════

-- SKU (Stock Keeping Unit): identificador semántico de negocio, NO un UUID.
-- Codifica información legible: KB-MECH-001 = Keyboard, Mecánico, secuencial 001.
-- Usado en facturas, etiquetas de bodega, comunicación con proveedores y clientes B2B.
-- Es el "lenguaje ubicuo" del dominio de inventario (DDD).

CREATE TABLE IF NOT EXISTS stock (
    id        UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    sku       VARCHAR(50)    NOT NULL UNIQUE,
    quantity  INTEGER        NOT NULL DEFAULT 0 CHECK (quantity >= 0)
);

CREATE TABLE IF NOT EXISTS stock_reservations (
    id              UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id        UUID           NOT NULL,
    sku             VARCHAR(50)    NOT NULL,
    quantity        INTEGER        NOT NULL CHECK (quantity > 0),
    status          VARCHAR(30)    NOT NULL DEFAULT 'ACTIVE',
    expires_at      TIMESTAMP      NOT NULL DEFAULT (now() + INTERVAL '15 minutes'),
    created_at      TIMESTAMP      NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS stock_movements (
    id              UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    sku             VARCHAR(50)    NOT NULL,
    movement_type   VARCHAR(30)    NOT NULL,
    quantity        INTEGER        NOT NULL,
    reference_id    UUID,
    created_at      TIMESTAMP      NOT NULL DEFAULT now()
);

-- Outbox Pattern: eventos pendientes de publicar a Kafka.
-- No se almacena el tópico porque ms-inventory siempre publica a "inventory-events".
-- El relay lo tiene como configuración fija del servicio.
CREATE TABLE IF NOT EXISTS outbox_events (
    id              UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    -- aggregate_id: ID de la entidad raíz que generó el evento.
    -- En ms-inventory se usa el SKU (texto) como partition key de Kafka,
    -- pero aquí se almacena como texto para soportar ambos formatos.
    aggregate_id    VARCHAR(50)    NOT NULL,
    event_type      VARCHAR(100)   NOT NULL,
    payload         JSONB          NOT NULL,
    created_at      TIMESTAMP      NOT NULL DEFAULT now(),
    published       BOOLEAN        NOT NULL DEFAULT FALSE,
    published_at    TIMESTAMP
);

-- Idempotencia
CREATE TABLE IF NOT EXISTS processed_events (
    event_id        UUID           PRIMARY KEY,
    processed_at    TIMESTAMP      NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_stock_sku               ON stock (sku);
CREATE INDEX IF NOT EXISTS idx_reservations_order       ON stock_reservations (order_id);
CREATE INDEX IF NOT EXISTS idx_reservations_status      ON stock_reservations (status);
CREATE INDEX IF NOT EXISTS idx_reservations_expires     ON stock_reservations (expires_at) WHERE status = 'ACTIVE';
CREATE INDEX IF NOT EXISTS idx_movements_sku            ON stock_movements (sku);
CREATE INDEX IF NOT EXISTS idx_outbox_unpublished       ON outbox_events (published) WHERE published = FALSE;

-- ═══════════════════════════════════════════════════
-- Seed data: productos de prueba
-- ═══════════════════════════════════════════════════
INSERT INTO stock (id, sku, quantity) VALUES
    (gen_random_uuid(), 'KB-MECH-001', 50),
    (gen_random_uuid(), 'MS-WIRE-002', 120),
    (gen_random_uuid(), 'MN-UW-003',  15),
    (gen_random_uuid(), 'GPU-RTX-004', 8),
    (gen_random_uuid(), 'RAM-DDR5-005', 60)
ON CONFLICT (sku) DO NOTHING;
