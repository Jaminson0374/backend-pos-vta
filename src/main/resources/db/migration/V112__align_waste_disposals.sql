-- =====================================================================
-- V112: Align stock_disposals with the Waste_Disposals contract (§7.A)
--   - Rename table stock_disposals -> waste_disposals
--   - Align disposal_type -> disposition_type values
--   - Add official_document, disposal_date, journal_entry_id, registered_by
--   - Replace created_by (VARCHAR 'SYSTEM') with registered_by (UUID user)
-- =====================================================================

ALTER TABLE stock_disposals RENAME TO waste_disposals;

-- The auto-generated check constraint keeps its old name after the rename.
ALTER TABLE waste_disposals DROP CONSTRAINT IF EXISTS stock_disposals_disposal_type_check;

ALTER TABLE waste_disposals RENAME COLUMN disposal_type TO disposition_type;

UPDATE waste_disposals SET disposition_type = 'DECOMISO_SANITARIO' WHERE disposition_type = 'SANITARIO';

ALTER TABLE waste_disposals ADD CONSTRAINT waste_disposals_disposition_type_check
    CHECK (disposition_type IN ('DECOMISO_SANITARIO','RESIDUO_VENDIBLE','MERMA_PROCESO'));

ALTER TABLE waste_disposals ADD COLUMN IF NOT EXISTS official_document TEXT;
ALTER TABLE waste_disposals ADD COLUMN IF NOT EXISTS disposal_date DATE;
ALTER TABLE waste_disposals ADD COLUMN IF NOT EXISTS journal_entry_id UUID REFERENCES journal_entries(id);

ALTER TABLE waste_disposals DROP COLUMN IF EXISTS created_by;
ALTER TABLE waste_disposals ADD COLUMN IF NOT EXISTS registered_by UUID REFERENCES users(id);

CREATE INDEX IF NOT EXISTS idx_wd_registered_by ON waste_disposals(registered_by);
