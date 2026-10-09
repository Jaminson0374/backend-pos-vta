-- ============================================================
-- V110: Align production_batches.formula_id FK to products(id)
-- V60 created production_batches.formula_id REFERENCES product_formulas(id),
-- but FormulaProductionUseCase stores the produced PRODUCT id there — consistent
-- with production_orders.formula_id, which V69 already points at products(id).
-- ============================================================

ALTER TABLE production_batches
    DROP CONSTRAINT IF EXISTS production_batches_formula_id_fkey;

ALTER TABLE production_batches
    ADD CONSTRAINT production_batches_formula_id_fkey
    FOREIGN KEY (formula_id) REFERENCES products(id);
