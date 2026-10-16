-- ============================================================
-- V120: Retirar el IVA 8% (Impoconsumo) del esquema de ventas.
-- La tabla normativa de tributación es 0/5/19. Se elimina la columna
-- sales_documents.total_tax_8 y se restringe tax_type en products y
-- purchase_line_items a EXENTO/IVA_5/IVA_19.
--
-- Guardas: la migración aborta si todavía existen datos con 8%
-- (IVA_8 o total_tax_8 distinto de cero) para forzar una
-- reclasificación manual antes de desplegar; no reclasifica en silencio.
-- ============================================================

DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM products WHERE tax_type = 'IVA_8')
     OR EXISTS (SELECT 1 FROM purchase_line_items WHERE tax_type = 'IVA_8')
     OR EXISTS (SELECT 1 FROM sales_items WHERE tax_type = 'IVA_8' OR tax_rate = 8)
     OR EXISTS (SELECT 1 FROM custom_prices WHERE tax_type = 'IVA_8')
     OR EXISTS (SELECT 1 FROM sales_documents WHERE total_tax_8 <> 0) THEN
    RAISE EXCEPTION 'V120 abortada: hay datos con IVA_8/total_tax_8; reclasificar manualmente antes de desplegar';
  END IF;
END $$;

ALTER TABLE sales_documents DROP COLUMN total_tax_8;

ALTER TABLE products DROP CONSTRAINT products_tax_type_check;
ALTER TABLE products ADD CONSTRAINT products_tax_type_check
  CHECK (tax_type IN ('EXENTO','IVA_5','IVA_19'));

ALTER TABLE purchase_line_items DROP CONSTRAINT purchase_line_items_tax_type_check;
ALTER TABLE purchase_line_items ADD CONSTRAINT purchase_line_items_tax_type_check
  CHECK (tax_type IN ('EXENTO','IVA_5','IVA_19'));
