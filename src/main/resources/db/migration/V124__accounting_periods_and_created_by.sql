-- =====================================================================
-- V124: Accounting periods and journal entry author (Contabilidad Fase 2, A5 + A7)
--
-- A5: journal entries must be dated with the source document date and must
--     abort when that date falls in an explicitly CLOSED accounting period.
--     A missing row means the period is OPEN by default, so existing flows
--     are not broken.
-- A7: traceability of who posted each entry through created_by.
-- =====================================================================

CREATE TABLE IF NOT EXISTS accounting_periods (
    period    VARCHAR(7) PRIMARY KEY,           -- 'YYYY-MM'
    status    VARCHAR(10) NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN','CLOSED')),
    opened_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    closed_at TIMESTAMPTZ,
    closed_by UUID REFERENCES users(id)
);

ALTER TABLE journal_entries ADD COLUMN IF NOT EXISTS created_by UUID REFERENCES users(id);
