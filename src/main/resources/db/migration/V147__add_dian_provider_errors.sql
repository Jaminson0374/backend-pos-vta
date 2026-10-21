-- ============================================================
-- V147: D2.3 — Errores estructurados devueltos por Siigo.
-- El cuerpo de un 400 se conserva para que la UI muestre el campo
-- y la regla exacta, en lugar de un mensaje genérico.
-- ============================================================

ALTER TABLE electronic_invoices
    ADD COLUMN IF NOT EXISTS provider_errors JSONB;

COMMENT ON COLUMN electronic_invoices.provider_errors IS
    'Array JSON con {code, message, params, detail} devuelto por Siigo en un rechazo.';
