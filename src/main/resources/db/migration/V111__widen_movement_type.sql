-- ============================================================
-- V111: Widen inventory_movements.movement_type
-- V63 extended the CHECK constraint with the production movement types
-- (PRODUCTION_CONSUMPTION / PRODUCTION_OUTPUT / PRODUCTION_SHRINKAGE), but the column
-- was left at VARCHAR(20). PRODUCTION_CONSUMPTION is 22 chars and overflows.
-- ============================================================

ALTER TABLE inventory_movements
    ALTER COLUMN movement_type TYPE VARCHAR(30);
