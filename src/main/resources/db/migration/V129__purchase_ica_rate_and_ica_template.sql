-- ============================================================
-- V129: Purchase ICA rate (company level) + ICA retention template entry
-- Contabilidad Fase 2 — hallazgos A3/A4 (retenciones data-driven).
--
-- 1. company_config.purchase_ica_rate: empresa-level ICA withholding rate used
--    by SupplierInvoiceUseCase to compute the ICA retention on the purchase base.
--    Mirrors V88's purchase_retefuente_rate shape (NUMERIC(5,2), 0..100).
--
--    NOTE on purchase_retefuente_rate (V88): it is superseded by the data-driven
--    purchase_retention_configs catalog (V89/V90). Retefuente is now selected per
--    supplier from that catalog instead of read from this single column, so this
--    migration intentionally leaves purchase_retefuente_rate in place for
--    backward compatibility but no longer wires it into the calculation.
--
-- 2. PUC account 2368 "ICA retenido por pagar", mirroring how 2365 is used by
--    the DEFAULT_PURCHASE template (level 3 posting account).
--
-- 3. PURCHASE_RETENTION_ICA template entry in DEFAULT_PURCHASE so the ICA branch
--    of AccountingEventListener.onPurchase can actually post. Before this entry
--    existed the ICA branch never fired and ICA was always 0.
-- ============================================================

-- 1. Empresa-level ICA withholding rate (default 0, 0..100).
ALTER TABLE company_config
  ADD COLUMN IF NOT EXISTS purchase_ica_rate NUMERIC(5,2) NOT NULL DEFAULT 0
    CHECK (purchase_ica_rate >= 0 AND purchase_ica_rate <= 100);

-- 2. ICA withholding account (level 3, like 2365 — posted directly by templates).
INSERT INTO puc_accounts (code, name, level, parent_code, account_class, account_nature, allows_transactions)
VALUES ('2368', 'Impuesto de industria y comercio retenido', 3, '23', 2, 'CREDITO', false)
ON CONFLICT (code) DO NOTHING;

-- 3. PURCHASE_RETENTION_ICA entry → 2368 (CREDITO) in the default purchase template.
INSERT INTO accounting_template_entries (template_id, event_type, account_id, is_debit, priority)
SELECT t.id, 'PURCHASE_RETENTION_ICA', a.id, false, 6
FROM accounting_templates t, puc_accounts a
WHERE t.code = 'DEFAULT_PURCHASE' AND a.code = '2368'
ON CONFLICT (template_id, event_type, account_id) DO NOTHING;
