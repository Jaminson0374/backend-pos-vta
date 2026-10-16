-- ============================================================
-- V128: Cuentas de faltante/sobrante de caja y tipo SHIFT_CLOSE
--       — Contabilidad Fase 2 (WU-36d)
--
-- Al cerrar un turno con diferencia de arqueo se postea un ajuste:
--   * Faltante (esperado > contado): débito FALTANTE DE CAJA / crédito Caja 1105.
--   * Sobrante (contado > esperado): débito Caja 1105 / crédito SOBRANTE DE CAJA.
--   * Diferencia 0: no se postea nada.
--
-- Cuentas sembradas (nivel 4, postables):
--   529520  FALTANTE DE CAJA → bajo 5295 DIVERSOS (clase 5, naturaleza DÉBITO).
--           Código libre (529505/529510/529515 ya en uso en V98/V125).
--   429515  SOBRANTE DE CAJA → bajo 4295 DIVERSOS (clase 4, naturaleza CRÉDITO).
--           Código libre (429505/429510 ya en uso en V98).
--
-- Además se habilita 'SHIFT_CLOSE' como source_type de asientos automáticos:
-- el CHECK original de V72 solo listaba SALE/PURCHASE/INVENTORY/PAYMENT/PRODUCTION/MANUAL.
--
-- Idempotente: INSERT ... ON CONFLICT (code) DO NOTHING + DROP CONSTRAINT IF EXISTS.
-- ============================================================

INSERT INTO puc_accounts (code,name,level,parent_code,account_class,account_nature,allows_transactions) VALUES
('529520','FALTANTE DE CAJA', 4,'5295',5,'DEBITO', true),
('429515','SOBRANTE DE CAJA', 4,'4295',4,'CREDITO',true)
ON CONFLICT (code) DO NOTHING;

ALTER TABLE journal_entries DROP CONSTRAINT IF EXISTS journal_entries_source_type_check;
ALTER TABLE journal_entries ADD CONSTRAINT journal_entries_source_type_check
    CHECK (source_type IN ('SALE','PURCHASE','INVENTORY','PAYMENT','PRODUCTION','MANUAL','SHIFT_CLOSE'));
