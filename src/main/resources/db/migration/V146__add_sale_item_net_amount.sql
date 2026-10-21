-- ============================================================
-- V146: D2.2 — Base gravable neta por línea.
--
-- `subtotal` sigue siendo el bruto (cantidad × precio). `net_amount`
-- es la base después del descuento de línea y, en ventas POS, después
-- de la parte prorrateada del descuento de documento. Es la base que
-- se agrupa por tarifa 0/5/19 para el payload del proveedor.
-- ============================================================

ALTER TABLE sales_items
    ADD COLUMN IF NOT EXISTS net_amount NUMERIC(19,2);

UPDATE sales_items
SET net_amount = subtotal - discount_amount
WHERE net_amount IS NULL;

ALTER TABLE sales_items
    ALTER COLUMN net_amount SET DEFAULT 0;

ALTER TABLE sales_items
    ALTER COLUMN net_amount SET NOT NULL;

COMMENT ON COLUMN sales_items.net_amount IS
    'Base gravable neta de la línea, después de descuentos de línea y prorrateo del descuento de documento.';
