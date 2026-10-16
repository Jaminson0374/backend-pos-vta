-- ============================================================
-- V130: Manual journal-entry justification (Contabilidad Fase 2 — hallazgo A8 + M5)
--
-- A8: manual journal entries stay allowed, but only for ADMIN/CONTADOR and only with a
--     mandatory, auditable justification. This column stores that justification.
--     Automatic entries (source_type != 'MANUAL') leave it NULL: they are self-describing
--     through their source document. Manual entries enforce NOT NULL at the application
--     boundary (ManualJournalEntryRequest @NotBlank), not at the DB level, so the existing
--     automatic inserts keep working unchanged.
-- ============================================================

ALTER TABLE journal_entries ADD COLUMN IF NOT EXISTS justification VARCHAR(500);
