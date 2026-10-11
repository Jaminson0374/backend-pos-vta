-- ============================================================
-- V115: Numeración fiscal por resolución DIAN (Fase 2 — WU-5/C2)
-- Agrega el consecutivo actual de cada resolución, valida los rangos
-- configurados y garantiza que el número de documento de venta sea
-- único (la numeración ya no puede repetirse).
-- ============================================================

ALTER TABLE dian_resolutions ADD COLUMN current_number BIGINT;

ALTER TABLE dian_resolutions ADD CONSTRAINT chk_dian_res_range CHECK (range_from <= range_to);
ALTER TABLE dian_resolutions ADD CONSTRAINT chk_dian_res_validity CHECK (valid_from <= valid_to);

CREATE UNIQUE INDEX IF NOT EXISTS uq_sales_documents_document_number ON sales_documents(document_number);
