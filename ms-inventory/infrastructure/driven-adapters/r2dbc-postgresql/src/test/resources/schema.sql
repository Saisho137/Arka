-- Simplified schema for Testcontainers integration tests
CREATE TABLE IF NOT EXISTS stock (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sku VARCHAR(50) NOT NULL UNIQUE,
    product_id UUID NOT NULL,
    quantity INTEGER NOT NULL CHECK (quantity >= 0),
    reserved_quantity INTEGER NOT NULL DEFAULT 0 CHECK (reserved_quantity >= 0),
    depletion_threshold INTEGER NOT NULL DEFAULT 10 CHECK (depletion_threshold >= 0),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 1,
    CONSTRAINT chk_reserved_not_exceeds_quantity CHECK (reserved_quantity <= quantity)
);
