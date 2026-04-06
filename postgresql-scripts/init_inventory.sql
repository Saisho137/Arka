-- ═══════════════════════════════════════════════════
-- Arka · db_inventory · Initialization Script
-- Microservice: ms-inventory (Stock, Reservas, Locks)
-- ═══════════════════════════════════════════════════

DO $$ BEGIN
    CREATE TYPE movement_type AS ENUM (
        'RESTOCK',
        'SHRINKAGE',
        'ORDER_RESERVE',
        'ORDER_CONFIRM',
        'RESERVATION_RELEASE',
        'PRODUCT_CREATION'
    );
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;

DO $$ BEGIN
    CREATE TYPE reservation_status AS ENUM (
        'PENDING',
        'CONFIRMED',
        'EXPIRED',
        'RELEASED'
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

DO $$ BEGIN
    CREATE TYPE event_type AS ENUM (
        'STOCK_RESERVED',
        'STOCK_RESERVE_FAILED',
        'STOCK_RELEASED',
        'STOCK_UPDATED',
        'STOCK_DEPLETED'
    );
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;

CREATE TABLE IF NOT EXISTS stock (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid (),
    sku VARCHAR(50) NOT NULL UNIQUE,
    product_id UUID NOT NULL,
    quantity INTEGER NOT NULL CHECK (quantity >= 0),
    reserved_quantity INTEGER NOT NULL DEFAULT 0 CHECK (reserved_quantity >= 0),
    available_quantity INTEGER GENERATED ALWAYS AS (quantity - reserved_quantity) STORED,
    depletion_threshold INTEGER NOT NULL DEFAULT 10 CHECK (depletion_threshold >= 0),
    updated_at TIMESTAMPTZ DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 1,
    CONSTRAINT chk_reserved_not_exceeds_quantity CHECK (reserved_quantity <= quantity)
);

CREATE TABLE IF NOT EXISTS stock_reservations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid (),
    sku VARCHAR(50) NOT NULL,
    order_id UUID NOT NULL,
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    status reservation_status NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE IF NOT EXISTS stock_movements (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid (),
    sku VARCHAR(50) NOT NULL,
    movement_type movement_type NOT NULL,
    quantity_change INTEGER NOT NULL,
    previous_quantity INTEGER NOT NULL CHECK (previous_quantity >= 0),
    new_quantity INTEGER NOT NULL CHECK (new_quantity >= 0),
    order_id UUID,
    reason TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS outbox_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid (),
    event_type event_type NOT NULL,
    payload JSONB NOT NULL,
    partition_key VARCHAR(50),
    status outbox_status NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS processed_events (
    event_id UUID PRIMARY KEY,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_stock_sku ON stock (sku);

CREATE INDEX IF NOT EXISTS idx_reservations_status_expires ON stock_reservations (status, expires_at);

CREATE INDEX IF NOT EXISTS idx_reservations_sku_order ON stock_reservations (sku, order_id, status);

CREATE INDEX IF NOT EXISTS idx_movements_sku_created ON stock_movements (sku, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_outbox_status_created ON outbox_events (status, created_at);

-- Seed data
INSERT INTO
    stock (id, sku, product_id, quantity, depletion_threshold)
VALUES (
        gen_random_uuid (),
        'KB-MECH-001',
        gen_random_uuid (),
        50,
        10
    ),
    (
        gen_random_uuid (),
        'MS-WIRE-002',
        gen_random_uuid (),
        120,
        20
    ),
    (
        gen_random_uuid (),
        'MN-UW-003',
        gen_random_uuid (),
        15,
        3
    ),
    (
        gen_random_uuid (),
        'GPU-RTX-004',
        gen_random_uuid (),
        8,
        2
    ),
    (
        gen_random_uuid (),
        'RAM-DDR5-005',
        gen_random_uuid (),
        60,
        10
    ) ON CONFLICT (sku) DO NOTHING;