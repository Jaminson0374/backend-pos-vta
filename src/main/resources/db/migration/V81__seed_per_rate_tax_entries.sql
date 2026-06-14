-- ============================================================
-- V81: Seed per-rate SALE_TAX entries for DEFAULT_SALE template
-- Replaces single SALE_TAX→2408 with three per-rate entries:
--   240805 (IVA 5%), 240810 (IVA 8%), 240815 (IVA 19%)
-- ============================================================

-- Remove old single SALE_TAX→2408 entry from DEFAULT_SALE
DELETE FROM accounting_template_entries ate
USING accounting_templates at
WHERE ate.template_id = at.id
  AND at.code = 'DEFAULT_SALE'
  AND ate.event_type = 'SALE_TAX'
  AND ate.account_id IN (SELECT id FROM puc_accounts WHERE code = '2408');

-- Insert SALE_TAX → 240805 (IVA 5%, CREDITO, priority 5)
INSERT INTO accounting_template_entries (template_id, event_type, account_id, is_debit, priority)
SELECT t.id, 'SALE_TAX', a.id, false, 5
FROM accounting_templates t, puc_accounts a
WHERE t.code = 'DEFAULT_SALE' AND a.code = '240805';

-- Insert SALE_TAX → 240810 (IVA 8%, CREDITO, priority 6)
INSERT INTO accounting_template_entries (template_id, event_type, account_id, is_debit, priority)
SELECT t.id, 'SALE_TAX', a.id, false, 6
FROM accounting_templates t, puc_accounts a
WHERE t.code = 'DEFAULT_SALE' AND a.code = '240810';

-- Insert SALE_TAX → 240815 (IVA 19%, CREDITO, priority 7)
INSERT INTO accounting_template_entries (template_id, event_type, account_id, is_debit, priority)
SELECT t.id, 'SALE_TAX', a.id, false, 7
FROM accounting_templates t, puc_accounts a
WHERE t.code = 'DEFAULT_SALE' AND a.code = '240815';
