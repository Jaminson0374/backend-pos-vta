-- ============================================================
-- V79: Relax accounting_template_entries unique constraint
-- From: UNIQUE(template_id, event_type)
-- To:   UNIQUE(template_id, event_type, account_id)
-- This allows multiple SALE_TAX entries per template (one per rate)
-- ============================================================

ALTER TABLE accounting_template_entries DROP CONSTRAINT IF EXISTS accounting_template_e_template_id_event_type_key;

ALTER TABLE accounting_template_entries ADD CONSTRAINT accounting_template_e_template_event_account_key UNIQUE (template_id, event_type, account_id);
