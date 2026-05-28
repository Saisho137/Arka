-- ═══════════════════════════════════════════════════
-- Arka · db_provider · Initialization Script
-- Microservice: ms-provider (ACL Proveedores)
-- ═══════════════════════════════════════════════════

CREATE TABLE IF NOT EXISTS suppliers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(200) NOT NULL,
    email VARCHAR(200) NOT NULL UNIQUE,
    phone VARCHAR(50),
    address TEXT,
    country VARCHAR(3) NOT NULL DEFAULT 'CO',
    active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_suppliers_email ON suppliers(email);
CREATE INDEX IF NOT EXISTS idx_suppliers_active ON suppliers(active);

CREATE TABLE IF NOT EXISTS supplier_products (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    supplier_id UUID NOT NULL REFERENCES suppliers(id),
    sku VARCHAR(50) NOT NULL,
    supplier_sku VARCHAR(100),
    unit_price NUMERIC(12,2) NOT NULL,
    lead_time_days INTEGER NOT NULL DEFAULT 7,
    reorder_multiplier NUMERIC(4,2) NOT NULL DEFAULT 2.0,
    preferred BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_supplier_sku UNIQUE (supplier_id, sku)
);

CREATE INDEX IF NOT EXISTS idx_supplier_products_sku ON supplier_products(sku);
CREATE INDEX IF NOT EXISTS idx_supplier_products_preferred ON supplier_products(sku, preferred) WHERE preferred = true;

CREATE TABLE IF NOT EXISTS purchase_orders (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    supplier_id UUID NOT NULL REFERENCES suppliers(id),
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    total_amount NUMERIC(14,2) NOT NULL,
    notes TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    sent_at TIMESTAMP,
    confirmed_at TIMESTAMP,
    received_at TIMESTAMP,
    CONSTRAINT chk_po_status CHECK (status IN ('PENDING', 'SENT', 'CONFIRMED', 'PARTIALLY_RECEIVED', 'RECEIVED', 'CANCELLED'))
);

CREATE INDEX IF NOT EXISTS idx_purchase_orders_supplier ON purchase_orders(supplier_id);
CREATE INDEX IF NOT EXISTS idx_purchase_orders_status ON purchase_orders(status);
CREATE INDEX IF NOT EXISTS idx_purchase_orders_created ON purchase_orders(created_at DESC);

CREATE TABLE IF NOT EXISTS purchase_order_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    purchase_order_id UUID NOT NULL REFERENCES purchase_orders(id),
    sku VARCHAR(50) NOT NULL,
    product_name VARCHAR(200),
    quantity INTEGER NOT NULL,
    unit_price NUMERIC(12,2) NOT NULL,
    subtotal NUMERIC(14,2) NOT NULL,
    CONSTRAINT chk_quantity_positive CHECK (quantity > 0)
);

CREATE INDEX IF NOT EXISTS idx_poi_purchase_order ON purchase_order_items(purchase_order_id);
CREATE INDEX IF NOT EXISTS idx_poi_sku ON purchase_order_items(sku);

CREATE TABLE IF NOT EXISTS outbox_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_type VARCHAR(50) NOT NULL,
    topic VARCHAR(100) NOT NULL DEFAULT 'provider-events',
    partition_key VARCHAR(100) NOT NULL,
    payload JSONB NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_outbox_status CHECK (status IN ('PENDING', 'PUBLISHED'))
);

CREATE INDEX IF NOT EXISTS idx_outbox_status_created ON outbox_events(status, created_at) WHERE status = 'PENDING';

CREATE TABLE IF NOT EXISTS processed_events (
    event_id UUID PRIMARY KEY,
    processed_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_processed_events_processed_at ON processed_events(processed_at);

-- Seed data: Demo supplier
INSERT INTO suppliers (id, name, email, phone, address, country, active)
VALUES ('a1b2c3d4-e5f6-7890-abcd-ef1234567890', 'TechParts Colombia S.A.S', 'ventas@techparts.co', '+57 1 234 5678', 'Cra 15 #93-47 Of 501, Bogotá', 'CO', true)
ON CONFLICT (email) DO NOTHING;

INSERT INTO supplier_products (supplier_id, sku, supplier_sku, unit_price, lead_time_days, reorder_multiplier, preferred)
VALUES ('a1b2c3d4-e5f6-7890-abcd-ef1234567890', 'KB-MECH-001', 'TP-KB-001', 45000.00, 5, 2.0, true)
ON CONFLICT (supplier_id, sku) DO NOTHING;

INSERT INTO supplier_products (supplier_id, sku, supplier_sku, unit_price, lead_time_days, reorder_multiplier, preferred)
VALUES ('a1b2c3d4-e5f6-7890-abcd-ef1234567890', 'MS-WL-002', 'TP-MS-002', 32000.00, 5, 2.0, true)
ON CONFLICT (supplier_id, sku) DO NOTHING;

INSERT INTO supplier_products (supplier_id, sku, supplier_sku, unit_price, lead_time_days, reorder_multiplier, preferred)
VALUES ('a1b2c3d4-e5f6-7890-abcd-ef1234567890', 'MN-27-003', 'TP-MN-003', 890000.00, 7, 1.5, true)
ON CONFLICT (supplier_id, sku) DO NOTHING;
