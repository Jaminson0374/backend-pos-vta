-- ============================================================
-- V96: Add product_id to batches, drop FK on goods_receipts,
-- backfill product_id from inventory_stock
-- (Sprint 20 — Consolidación de Lotes)
-- ============================================================

-- Step 1: Drop FK constraint on source_receipt_id → goods_receipts
-- ---------------------------------------------------------------------------
-- V27 added: source_receipt_id UUID REFERENCES goods_receipts(id)
-- V32 made it DEFERRABLE and renamed it to batches_source_receipt_id_fkey.
--
-- Logistics receipts live in the `receipts` table, not `goods_receipts`.
-- This FK blocks logistics receipt references via source_receipt_id.
-- Application-layer validation (BatchUseCase) replaces this DB constraint
-- with explicit repo lookups in both receipts and goods_receipts tables.
-- ---------------------------------------------------------------------------
ALTER TABLE batches DROP CONSTRAINT IF EXISTS batches_source_receipt_id_fkey;

-- Step 2: Add product_id column (nullable initially for backfill)
ALTER TABLE batches ADD COLUMN IF NOT EXISTS product_id UUID;

-- Step 3: Backfill product_id from inventory_stock (authoritative source)
-- ---------------------------------------------------------------------------
-- inventory_stock has a UNIQUE(product_id, batch_id, warehouse_id) constraint
-- and an FK batch_id → batches(id). Each batch should have at least one
-- inventory_stock row with the product_id.
--
-- Using a correlated subquery with LIMIT 1 to pick any matching row.
-- Batches with no inventory_stock row are handled in Step 4.
-- ---------------------------------------------------------------------------
UPDATE batches b SET product_id = (
    SELECT is2.product_id FROM inventory_stock is2
    WHERE is2.batch_id = b.id LIMIT 1
) WHERE b.product_id IS NULL;

-- Step 4: Fallback — batches without inventory_stock get a sentinel UUID
-- ---------------------------------------------------------------------------
-- Sentinel UUID: 00000000-0000-0000-0000-000000000001
-- Represents "PRODUCTO NO IDENTIFICADO" — a placeholder for batches
-- that have no inventory_stock association (e.g., migrated data, orphaned batches).
--
-- TODO (sprint-20): Create a sentinel product row with this UUID
--   INSERT INTO products (id, product_code, name, category, is_transformable, is_active)
--   VALUES ('00000000-0000-0000-0000-000000000001', 'SENTINEL', 'PRODUCTO NO IDENTIFICADO', 'GENERAL', false, false);
--
--   After seeding, validate the FK constraint:
--   ALTER TABLE batches VALIDATE CONSTRAINT fk_batches_product_id;
-- ---------------------------------------------------------------------------
UPDATE batches SET product_id = '00000000-0000-0000-0000-000000000001'
WHERE product_id IS NULL;

-- Step 5: Enforce NOT NULL
-- All rows have product_id after Steps 3 and 4 — safe to add NOT NULL.
ALTER TABLE batches ALTER COLUMN product_id SET NOT NULL;

-- Step 6: Add FK to products (NOT VALID)
-- ---------------------------------------------------------------------------
-- NOT VALID skips scanning existing rows at constraint creation time,
-- avoiding a SHARE ROW EXCLUSIVE lock on the batches table.
-- New INSERT/UPDATE operations ARE validated against the constraint.
--
-- Existing rows (including the sentinel UUID) are NOT checked.
-- Validation is deferred until the sentinel product is seeded (see Step 4 TODO).
-- ---------------------------------------------------------------------------
ALTER TABLE batches ADD CONSTRAINT fk_batches_product_id
    FOREIGN KEY (product_id) REFERENCES products(id)
    NOT VALID;

-- Step 7: Index for query performance (JOINs, WHERE filters on product_id)
CREATE INDEX IF NOT EXISTS idx_batches_product_id ON batches(product_id);
