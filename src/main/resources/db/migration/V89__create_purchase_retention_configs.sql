-- V89: Create purchase_retention_configs table
CREATE TABLE purchase_retention_configs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(30) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    rate NUMERIC(5,2) NOT NULL DEFAULT 0 CHECK (rate >= 0 AND rate <= 100),
    base_min NUMERIC(15,2) NOT NULL DEFAULT 0 CHECK (base_min >= 0),
    applies_to_tax_regime VARCHAR(50),
    applies_to_person_type VARCHAR(20),
    active BOOLEAN NOT NULL DEFAULT true,
    sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
