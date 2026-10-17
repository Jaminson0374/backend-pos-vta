-- ============================================================
-- V133: Tipos de asiento para anticipos a proveedores (S3b)
--
-- Un anticipo no aplicado es salida de caja que se convierte en activo:
--   * Creación: débito 133005 Anticipos a proveedores / crédito 1105 Caja.
--   * Aplicación a factura: débito 2205 Proveedores / crédito 133005 Anticipos.
--
-- Se habilitan 'ADVANCE' y 'ADVANCE_APPLICATION' como source_type de
-- asientos automáticos; el CHECK vigente solo listaba hasta 'SHIFT_CLOSE'.
-- source_type es VARCHAR(20), por lo que 'ADVANCE_APPLICATION' cabe.
--
-- Idempotente: DROP CONSTRAINT IF EXISTS + ADD CONSTRAINT.
-- ============================================================

ALTER TABLE journal_entries DROP CONSTRAINT IF EXISTS journal_entries_source_type_check;
ALTER TABLE journal_entries ADD CONSTRAINT journal_entries_source_type_check
    CHECK (source_type IN (
        'SALE','PURCHASE','INVENTORY','PAYMENT','PRODUCTION','MANUAL','SHIFT_CLOSE',
        'ADVANCE','ADVANCE_APPLICATION'
    ));
