-- ============================================================
-- V148: Catálogo de impuestos ampliado y FK en productos.
--
-- La tabla `taxes` ya existía como catálogo. Se amplía para ser
-- la fuente única del impuesto de producto (porcentaje, DIAN y Siigo)
-- y se reemplaza products.tax_type por una FK a taxes.code.
-- ============================================================

ALTER TABLE taxes
    ADD COLUMN IF NOT EXISTS percentage NUMERIC(5,2) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS observation VARCHAR(500),
    ADD COLUMN IF NOT EXISTS dian_code VARCHAR(2),
    ADD COLUMN IF NOT EXISTS siigo_tax_id VARCHAR(50);

ALTER TABLE taxes
    DROP CONSTRAINT IF EXISTS chk_tax_percentage,
    ADD CONSTRAINT chk_tax_percentage
        CHECK (percentage >= 0 AND percentage <= 100);

COMMENT ON COLUMN taxes.name IS 'Descripción del impuesto.';
COMMENT ON COLUMN taxes.percentage IS 'Porcentaje aplicable (0-100).';
COMMENT ON COLUMN taxes.observation IS 'Observación libre del impuesto.';
COMMENT ON COLUMN taxes.dian_code IS 'Código DIAN del tributo: 01 IVA, 04 INC, etc.';
COMMENT ON COLUMN taxes.siigo_tax_id IS 'ID del catálogo /v1/taxes de Siigo.';

-- Impuestos de producto que reemplazan el ENUM EXENTO/IVA_5/IVA_19.
INSERT INTO taxes (id, code, name, sort_order, active, percentage, dian_code)
VALUES
    (gen_random_uuid(), 'EXENTO', 'Exento / IVA 0%', 10, TRUE, 0.00, '01'),
    (gen_random_uuid(), 'IVA_5', 'IVA 5%', 11, TRUE, 5.00, '01'),
    (gen_random_uuid(), 'IVA_19', 'IVA 19%', 12, TRUE, 19.00, '01'),
    (gen_random_uuid(), 'INC_8', 'INC 8%', 13, TRUE, 8.00, '04')
ON CONFLICT (code) DO UPDATE
SET name = EXCLUDED.name,
    percentage = EXCLUDED.percentage,
    dian_code = EXCLUDED.dian_code,
    active = TRUE;

-- FK por código: el código es único y estable para POS/DIAN.
ALTER TABLE products
    ADD COLUMN IF NOT EXISTS tax_code VARCHAR(50);

UPDATE products
SET tax_code = tax_type
WHERE tax_code IS NULL;

UPDATE products
SET tax_code = 'EXENTO'
WHERE tax_code IS NULL OR btrim(tax_code) = '';

ALTER TABLE products
    ALTER COLUMN tax_code SET NOT NULL;

ALTER TABLE products
    DROP CONSTRAINT IF EXISTS fk_products_tax_code,
    ADD CONSTRAINT fk_products_tax_code
        FOREIGN KEY (tax_code) REFERENCES taxes(code);

ALTER TABLE products
    DROP CONSTRAINT IF EXISTS products_tax_type_check;

ALTER TABLE products
    DROP COLUMN IF EXISTS tax_type;

CREATE INDEX IF NOT EXISTS idx_products_tax_code ON products(tax_code);
