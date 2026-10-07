-- ─────────────────────────────────────────────────────────────────
-- V106 — Representante legal en campos libres (reemplaza la referencia a terceros)
-- ─────────────────────────────────────────────────────────────────

ALTER TABLE company_config
    ADD COLUMN IF NOT EXISTS legal_representative_identification_type_id UUID REFERENCES identification_types(id),
    ADD COLUMN IF NOT EXISTS legal_representative_document_number VARCHAR(40),
    ADD COLUMN IF NOT EXISTS legal_representative_name VARCHAR(200),
    ADD COLUMN IF NOT EXISTS legal_representative_position VARCHAR(100),
    ADD COLUMN IF NOT EXISTS legal_representative_address VARCHAR(255),
    ADD COLUMN IF NOT EXISTS legal_representative_email VARCHAR(255);

ALTER TABLE company_config DROP COLUMN IF EXISTS legal_representative_id;
