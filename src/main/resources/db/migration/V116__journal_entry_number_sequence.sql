-- ============================================================
-- V116: Concurrency-safe journal entry numbering (audit C2 follow-up)
-- Replaces the timestamp-derived entry number with a database
-- sequence so concurrent transactions never generate the same
-- entry_number (which violated the unique constraint under load).
-- ============================================================

CREATE SEQUENCE IF NOT EXISTS journal_entry_number_seq START 1;
