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

-- ─── Mock Data (desarrollo / pruebas) ─────────────
-- Emails de prueba y sus UUIDs derivados por ms-order (UUID.nameUUIDFromBytes):
--   admin@arka.com        → 2d66e954-4482-3e67-973c-7142c931083e
--   customer1@arka.com    → 482eae01-3840-3d80-9a3b-17333e6b32d5
--   customer2@arka.com    → 3e6c5f4e-ae19-32f9-a254-ba18570e280e

INSERT INTO orders (id, customer_id, customer_email, shipping_address, notes, status, total_amount, created_at, updated_at)
VALUES
    -- Orden 1: customer1, CONFIRMADO — listo para cambiar a EN_DESPACHO o cancelar
    ('550e8400-e29b-41d4-a716-446655440000',
     '482eae01-3840-3d80-9a3b-17333e6b32d5',
     'customer1@arka.com',
     'Calle 123 #45-67, Bogotá, Colombia',
     'Entregar en horario de oficina',
     'CONFIRMADO',
     1290000.00,
     now() - interval '2 days',
     now() - interval '2 days'),

    -- Orden 2: customer1, EN_DESPACHO — listo para cambiar a ENTREGADO
    ('550e8400-e29b-41d4-a716-446655440001',
     '482eae01-3840-3d80-9a3b-17333e6b32d5',
     'customer1@arka.com',
     'Carrera 7 #71-21, Bogotá, Colombia',
     NULL,
     'EN_DESPACHO',
     450000.00,
     now() - interval '5 days',
     now() - interval '1 day'),

    -- Orden 3: customer2, ENTREGADO — estado terminal
    ('550e8400-e29b-41d4-a716-446655440002',
     '3e6c5f4e-ae19-32f9-a254-ba18570e280e',
     'customer2@arka.com',
     'Avenida El Dorado #69-76, Bogotá, Colombia',
     'Producto frágil',
     'ENTREGADO',
     840000.00,
     now() - interval '10 days',
     now() - interval '3 days'),

    -- Orden 4: customer1, CANCELADO — estado terminal
    ('550e8400-e29b-41d4-a716-446655440003',
     '482eae01-3840-3d80-9a3b-17333e6b32d5',
     'customer1@arka.com',
     'Transversal 93 #50-23, Bogotá, Colombia',
     NULL,
     'CANCELADO',
     210000.00,
     now() - interval '7 days',
     now() - interval '6 days'),

    -- Orden 5: admin creando orden, CONFIRMADO con múltiples ítems
    ('550e8400-e29b-41d4-a716-446655440004',
     '2d66e954-4482-3e67-973c-7142c931083e',
     'admin@arka.com',
     'Calle 93 #14-20, Bogotá, Colombia',
     'Pedido de prueba con múltiples referencias',
     'CONFIRMADO',
     3750000.00,
     now() - interval '1 day',
     now() - interval '1 day')
ON CONFLICT (id) DO NOTHING;

INSERT INTO order_items (id, order_id, product_id, sku, product_name, quantity, unit_price)
VALUES
    -- Items orden 1
    ('a1b2c3d4-0000-0000-0000-000000000001',
     '550e8400-e29b-41d4-a716-446655440000',
     'f47ac10b-58cc-4372-a567-0e02b2c3d001',
     'KB-MECH-001', 'Teclado Mecánico RGB Pro', 3, 290000.00),
    ('a1b2c3d4-0000-0000-0000-000000000002',
     '550e8400-e29b-41d4-a716-446655440000',
     'f47ac10b-58cc-4372-a567-0e02b2c3d002',
     'MS-OPT-002', 'Mouse Óptico Inalámbrico', 3, 140000.00),

    -- Items orden 2
    ('a1b2c3d4-0000-0000-0000-000000000003',
     '550e8400-e29b-41d4-a716-446655440001',
     'f47ac10b-58cc-4372-a567-0e02b2c3d003',
     'MNT-27-001', 'Monitor 27 pulgadas 4K', 1, 450000.00),

    -- Items orden 3
    ('a1b2c3d4-0000-0000-0000-000000000004',
     '550e8400-e29b-41d4-a716-446655440002',
     'f47ac10b-58cc-4372-a567-0e02b2c3d004',
     'HDS-BT-003', 'Audífonos Bluetooth NC', 2, 420000.00),

    -- Items orden 4
    ('a1b2c3d4-0000-0000-0000-000000000005',
     '550e8400-e29b-41d4-a716-446655440003',
     'f47ac10b-58cc-4372-a567-0e02b2c3d005',
     'USB-HB-004', 'Hub USB-C 7 puertos', 3, 70000.00),

    -- Items orden 5 (múltiples referencias)
    ('a1b2c3d4-0000-0000-0000-000000000006',
     '550e8400-e29b-41d4-a716-446655440004',
     'f47ac10b-58cc-4372-a567-0e02b2c3d001',
     'KB-MECH-001', 'Teclado Mecánico RGB Pro', 5, 290000.00),
    ('a1b2c3d4-0000-0000-0000-000000000007',
     '550e8400-e29b-41d4-a716-446655440004',
     'f47ac10b-58cc-4372-a567-0e02b2c3d003',
     'MNT-27-001', 'Monitor 27 pulgadas 4K', 2, 450000.00),
    ('a1b2c3d4-0000-0000-0000-000000000008',
     '550e8400-e29b-41d4-a716-446655440004',
     'f47ac10b-58cc-4372-a567-0e02b2c3d002',
     'MS-OPT-002', 'Mouse Óptico Inalámbrico', 5, 140000.00)
ON CONFLICT (id) DO NOTHING;

INSERT INTO order_state_history (id, order_id, previous_status, new_status, changed_by, reason, created_at)
VALUES
    -- Historial orden 1: PENDIENTE_RESERVA → CONFIRMADO (sistema)
    ('b2c3d4e5-0000-0000-0000-000000000001',
     '550e8400-e29b-41d4-a716-446655440000',
     'PENDIENTE_RESERVA', 'CONFIRMADO',
     NULL, 'Stock reservado exitosamente',
     now() - interval '2 days'),

    -- Historial orden 2: PENDIENTE_RESERVA → CONFIRMADO → EN_DESPACHO
    ('b2c3d4e5-0000-0000-0000-000000000002',
     '550e8400-e29b-41d4-a716-446655440001',
     'PENDIENTE_RESERVA', 'CONFIRMADO',
     NULL, 'Stock reservado exitosamente',
     now() - interval '5 days'),
    ('b2c3d4e5-0000-0000-0000-000000000003',
     '550e8400-e29b-41d4-a716-446655440001',
     'CONFIRMADO', 'EN_DESPACHO',
     '2d66e954-4482-3e67-973c-7142c931083e', 'Entregado a transportista DHL',
     now() - interval '1 day'),

    -- Historial orden 3: PENDIENTE_RESERVA → CONFIRMADO → EN_DESPACHO → ENTREGADO
    ('b2c3d4e5-0000-0000-0000-000000000004',
     '550e8400-e29b-41d4-a716-446655440002',
     'PENDIENTE_RESERVA', 'CONFIRMADO',
     NULL, 'Stock reservado exitosamente',
     now() - interval '10 days'),
    ('b2c3d4e5-0000-0000-0000-000000000005',
     '550e8400-e29b-41d4-a716-446655440002',
     'CONFIRMADO', 'EN_DESPACHO',
     '2d66e954-4482-3e67-973c-7142c931083e', 'Entregado a transportista',
     now() - interval '7 days'),
    ('b2c3d4e5-0000-0000-0000-000000000006',
     '550e8400-e29b-41d4-a716-446655440002',
     'EN_DESPACHO', 'ENTREGADO',
     '2d66e954-4482-3e67-973c-7142c931083e', 'Entrega confirmada por destinatario',
     now() - interval '3 days'),

    -- Historial orden 4: PENDIENTE_RESERVA → CONFIRMADO → CANCELADO
    ('b2c3d4e5-0000-0000-0000-000000000007',
     '550e8400-e29b-41d4-a716-446655440003',
     'PENDIENTE_RESERVA', 'CONFIRMADO',
     NULL, 'Stock reservado exitosamente',
     now() - interval '7 days'),
    ('b2c3d4e5-0000-0000-0000-000000000008',
     '550e8400-e29b-41d4-a716-446655440003',
     'CONFIRMADO', 'CANCELADO',
     '482eae01-3840-3d80-9a3b-17333e6b32d5', 'Cliente canceló: cambio de proveedor',
     now() - interval '6 days'),

    -- Historial orden 5
    ('b2c3d4e5-0000-0000-0000-000000000009',
     '550e8400-e29b-41d4-a716-446655440004',
     'PENDIENTE_RESERVA', 'CONFIRMADO',
     NULL, 'Stock reservado exitosamente',
     now() - interval '1 day')
ON CONFLICT (id) DO NOTHING;
