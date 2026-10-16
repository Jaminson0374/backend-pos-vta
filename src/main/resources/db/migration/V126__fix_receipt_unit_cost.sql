-- ============================================================
-- V126: Fix historical receipt unit-cost valuation (Opción 1)
-- ============================================================
-- Context
-- -------
-- CreateGoodsReceiptUseCase used to treat the request's `actualCost` (sent by the
-- frontend as the UNIT cost, see recepcion-form.ts -> ocLine.unitCost) as a line TOTAL:
--   * batches.purchase_cost  = actualCost                      (WRONG: unit, should be total)
--   * inventory_stock.unit_cost = actualCost / receivedQty     (WRONG: should be actualCost)
--   * inventory_movements.unit_cost (ENTRY) = actualCost / receivedQty  (WRONG: should be actualCost)
--
-- The system convention is purchase_cost = LINE TOTAL and unit cost = purchase_cost /
-- initial_weight (see ManualDesposteUseCase / ProcessSlaughterUseCase). This migration
-- repairs rows for batches created by a goods receipt (source_receipt_id IS NOT NULL).
--
-- Assumption (pre-fix data)
-- -------------------------
-- For receipt-created batches, the stored (pre-fix) purchase_cost holds the UNIT cost
-- (the raw actualCost). It is therefore the source of truth for the corrected unit cost.
-- Note: purchase_cost is NUMERIC(15,2), so the recovered unit cost is limited to the
-- precision it was persisted with (2 decimals).
--
-- Order matters: steps 1 and 2 read the PRE-fix purchase_cost; step 3 then converts it
-- to the corrected line TOTAL.
-- ============================================================

-- 1) Stock: the stored unit_cost was actualCost / qty; the correct value is actualCost,
--    which equals the batch's pre-fix purchase_cost (the raw unit cost).
UPDATE inventory_stock s
SET unit_cost = b.purchase_cost
FROM batches b
WHERE s.batch_id = b.id
  AND b.source_receipt_id IS NOT NULL;

-- 2) Kardex: the receipt's ENTRY movements stored the divided unit cost; restore the
--    unit cost (actualCost = the batch's pre-fix purchase_cost). A batch created by a
--    receipt has exactly one ENTRY movement (reference GOODS_RECEIPT); no other flow
--    records ENTRY movements against such a batch.
UPDATE inventory_movements m
SET unit_cost = b.purchase_cost
FROM batches b
WHERE m.batch_id = b.id
  AND b.source_receipt_id IS NOT NULL
  AND m.movement_type = 'ENTRY';

-- 3) Batch: convert purchase_cost from UNIT to LINE TOTAL (unit × initial_weight).
--    Rows with a non-positive/NULL initial_weight (should not exist: CHECK initial_weight > 0)
--    are left untouched rather than guessed.
UPDATE batches
SET purchase_cost = ROUND(purchase_cost * initial_weight, 2)
WHERE source_receipt_id IS NOT NULL
  AND initial_weight IS NOT NULL
  AND initial_weight > 0;
