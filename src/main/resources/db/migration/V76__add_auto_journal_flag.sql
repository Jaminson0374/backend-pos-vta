-- ============================================================
-- V76: Add auto_generate_journal_entries flag to company_config
-- Controls whether journal entries are generated automatically
-- ============================================================

ALTER TABLE company_config
    ADD COLUMN IF NOT EXISTS auto_generate_journal_entries BOOLEAN NOT NULL DEFAULT true;
