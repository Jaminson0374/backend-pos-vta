-- ============================================================
-- V123: Idempotency guard for automatic journal entries (WU-33)
--
-- A single automatic journal entry may exist per (source_type, source_id).
-- Re-publishing the same business document event can no longer post a
-- second entry: the partial unique index rejects the duplicate insert.
--
-- The predicate is intentionally partial: MANUAL entries and any other
-- entry without a source document (source_id IS NULL) stay unrestricted,
-- so accountants can post as many manual entries as they need.
-- ============================================================

CREATE UNIQUE INDEX IF NOT EXISTS uq_journal_entries_source
    ON journal_entries(source_type, source_id)
    WHERE source_id IS NOT NULL;
