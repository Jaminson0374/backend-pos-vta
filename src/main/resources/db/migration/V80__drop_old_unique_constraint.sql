-- ============================================================
-- V80: Drop the old unique constraint on accounting_template_entries
-- V79 tried to drop it but used an incorrect constraint name.
-- This migration drops it by the PostgreSQL auto-generated name.
-- ============================================================

ALTER TABLE accounting_template_entries DROP CONSTRAINT IF EXISTS accounting_template_entries_template_id_event_type_key;
