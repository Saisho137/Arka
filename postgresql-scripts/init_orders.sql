-- ═══════════════════════════════════════════════════
-- Arka · db_orders · Initialization Script
-- Microservice: ms-order (Orquestador Saga Secuencial)
-- ═══════════════════════════════════════════════════

CREATE TABLE IF NOT EXISTS orders (
    id              UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id     VARCHAR(100)   NOT NULL,
    customer_email  VARCHAR(255)   NOT NULL,
    status          VARCHAR(30)    NOT NULL DEFAULT 'PENDIENTE_RESERVA',
    total_amount    NUMERIC(15,2)  NOT NULL DEFAULT 0 CHECK (total_amount >= 0),
    created_at      TIMESTAMP      NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP      NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS order_items (
    id            UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id      UUID           NOT NULL REFERENCES orders(id),
    -- SKU: identificador semántico de negocio (ej: KB-MECH-001).
    -- Texto legible usado en facturas, bodegas y comunicación con proveedores.
    sku           VARCHAR(50)    NOT NULL,
    product_name  VARCHAR(200)   NOT NULL,
    quantity      INTEGER        NOT NULL CHECK (quantity > 0),
    unit_price    NUMERIC(15,2)  NOT NULL CHECK (unit_price >= 0),
    subtotal      NUMERIC(15,2)  NOT NULL CHECK (subtotal >= 0)
);

-- Outbox Pattern: eventos pendientes de publicar a Kafka.
-- El relay asíncrono lee los no publicados y los empuja al broker.
-- No se almacena el tópico porque ms-order siempre publica a "order-events".
-- El relay lo tiene como configuración fija del servicio.
CREATE TABLE IF NOT EXISTS outbox_events (
    id              UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    -- aggregate_id: ID de la entidad raíz (aggregate root) que generó el evento.
    -- Se usa como partition key de Kafka para garantizar orden causal por agregado.
    -- En ms-order, corresponde al order_id.
    aggregate_id    UUID           NOT NULL,
    event_type      VARCHAR(100)   NOT NULL,
    payload         JSONB          NOT NULL,
    created_at      TIMESTAMP      NOT NULL DEFAULT now(),
    published       BOOLEAN        NOT NULL DEFAULT FALSE,
    published_at    TIMESTAMP
);

-- Idempotencia: eventos ya procesados (protección contra duplicados at-least-once)
CREATE TABLE IF NOT EXISTS processed_events (
    event_id        UUID           PRIMARY KEY,
    processed_at    TIMESTAMP      NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_orders_customer_id  ON orders (customer_id);
CREATE INDEX IF NOT EXISTS idx_orders_status       ON orders (status);
CREATE INDEX IF NOT EXISTS idx_order_items_order    ON order_items (order_id);
CREATE INDEX IF NOT EXISTS idx_outbox_unpublished   ON outbox_events (published) WHERE published = FALSE;
