-- ============================================================
-- V97: Add batch hierarchy columns — parent_batch_id, batch_type,
-- unit_of_measure_id with indexes and backfill
-- (Sprint 21 — Jerarquía de Lotes)
-- ============================================================

-- Step 1: Add parent_batch_id (self-referencing FK for hierarchy)
ALTER TABLE batches ADD COLUMN IF NOT EXISTS parent_batch_id UUID
    REFERENCES batches(id);

-- Step 2: Add batch_type (PARENT, CHILD, STANDARD)
-- STANDARD is the safe default for all existing rows — zero data loss.
ALTER TABLE batches ADD COLUMN IF NOT EXISTS batch_type VARCHAR(20)
    DEFAULT 'STANDARD'
    CHECK (batch_type IN ('PARENT', 'CHILD', 'STANDARD'));

-- Step 3: Add unit_of_measure_id (nullable — batch may differ from product default)
ALTER TABLE batches ADD COLUMN IF NOT EXISTS unit_of_measure_id UUID
    REFERENCES units_of_measure(id);

-- Step 4: Indexes for query performance
CREATE INDEX IF NOT EXISTS idx_batches_parent ON batches(parent_batch_id);
CREATE INDEX IF NOT EXISTS idx_batches_type ON batches(batch_type);
CREATE INDEX IF NOT EXISTS idx_batches_uom ON batches(unit_of_measure_id);

-- Step 5: Backfill — ensure no NULL batch_type survives
-- (Belt-and-suspenders: DEFAULT handles new rows, this catches legacy edge cases)
UPDATE batches SET batch_type = 'STANDARD' WHERE batch_type IS NULL;
