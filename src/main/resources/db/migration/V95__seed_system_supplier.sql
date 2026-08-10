-- ============================================================
-- V95: Seed system supplier for internal production + add batch_id
-- column to production_batches (Sprint 19 — Producción Batch Fix).
-- ============================================================

INSERT INTO third_parties (
    id, num_identification, name, type, person_type, is_active,
    created_at, updated_at, credit_limit, current_balance, tax_regime,
    credit_days, is_gran_contribuyente, is_autoretenedor,
    is_agente_retencion_iva, is_regimen_simple, other_tax_resp
) VALUES (
    gen_random_uuid(), '000000000-0', 'PRODUCCIÓN INTERNA',
    'SUPPLIER', 'JURIDICA', true, NOW(), NOW(),
    0, 0, 'ORDINARIO', 0,
    false, false, false, false, false
) ON CONFLICT (num_identification) DO NOTHING;

ALTER TABLE production_batches ADD COLUMN IF NOT EXISTS batch_id UUID REFERENCES batches(id);
