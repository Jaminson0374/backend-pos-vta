-- ============================================================
-- V135: Drop de la columna legacy third_parties.current_balance (A6-2)
--
-- A1–A5 cortaron todas las lecturas y escrituras. V134 alineó los AR
-- históricos de devoluciones. La columna ya no tiene consumidores.
-- ============================================================

ALTER TABLE third_parties DROP COLUMN IF EXISTS current_balance;
