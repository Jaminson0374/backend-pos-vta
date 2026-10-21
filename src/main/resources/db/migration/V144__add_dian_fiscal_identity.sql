-- ============================================================
-- V144: D2.1 — Identidad fiscal DIAN para emisor y adquiriente.
--
-- Siigo construye el XML UBL 2.1 y calcula CUFE/CUDE. El ERP solo
-- debe entregar códigos fiscales consistentes: tipo de identificación,
-- responsabilidades (TaxLevelCode), municipio (5 dígitos), departamento,
-- código postal y país.
-- ============================================================

-- ── Emisor ──────────────────────────────────────────────────────────────
ALTER TABLE company_config
    ADD COLUMN IF NOT EXISTS dian_identification_type_code VARCHAR(2) NOT NULL DEFAULT '31',
    ADD COLUMN IF NOT EXISTS dv VARCHAR(2),
    ADD COLUMN IF NOT EXISTS municipality_code VARCHAR(5),
    ADD COLUMN IF NOT EXISTS city_name VARCHAR(60),
    ADD COLUMN IF NOT EXISTS department_code VARCHAR(2),
    ADD COLUMN IF NOT EXISTS department_name VARCHAR(60),
    ADD COLUMN IF NOT EXISTS postal_code VARCHAR(10),
    ADD COLUMN IF NOT EXISTS country_code VARCHAR(2) NOT NULL DEFAULT 'CO',
    ADD COLUMN IF NOT EXISTS tax_level_code VARCHAR(30),
    ADD COLUMN IF NOT EXISTS dian_environment INTEGER;

ALTER TABLE company_config
    DROP CONSTRAINT IF EXISTS chk_company_dian_environment,
    ADD CONSTRAINT chk_company_dian_environment
        CHECK (dian_environment IS NULL OR dian_environment IN (1, 2)),
    DROP CONSTRAINT IF EXISTS chk_company_municipality_code,
    ADD CONSTRAINT chk_company_municipality_code
        CHECK (municipality_code IS NULL OR char_length(municipality_code) = 5);

COMMENT ON COLUMN company_config.dian_identification_type_code IS
    'Código DIAN del tipo de identificación del emisor; NIT = 31.';
COMMENT ON COLUMN company_config.dv IS
    'Dígito de verificación del NIT, separado del número.';
COMMENT ON COLUMN company_config.municipality_code IS
    'Código DANE del municipio de la dirección fiscal (5 dígitos).';
COMMENT ON COLUMN company_config.tax_level_code IS
    'Responsabilidades DIAN del emisor separadas por punto y coma, p. ej. O-13;O-15.';
COMMENT ON COLUMN company_config.dian_environment IS
    'Ambiente DIAN: 1 producción, 2 habilitación.';

-- ── Adquiriente / terceros ──────────────────────────────────────────────
ALTER TABLE third_parties
    ADD COLUMN IF NOT EXISTS dian_identification_type_code VARCHAR(2),
    ADD COLUMN IF NOT EXISTS dian_tax_level_code VARCHAR(30),
    ADD COLUMN IF NOT EXISTS municipality_code VARCHAR(5),
    ADD COLUMN IF NOT EXISTS city_name VARCHAR(60),
    ADD COLUMN IF NOT EXISTS department_code VARCHAR(2),
    ADD COLUMN IF NOT EXISTS department_name VARCHAR(60),
    ADD COLUMN IF NOT EXISTS postal_code VARCHAR(10),
    ADD COLUMN IF NOT EXISTS country_code VARCHAR(2) NOT NULL DEFAULT 'CO',
    ADD COLUMN IF NOT EXISTS registration_name VARCHAR(450),
    ADD COLUMN IF NOT EXISTS additional_account_id VARCHAR(1),
    ADD COLUMN IF NOT EXISTS is_consumer_final BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE third_parties
    DROP CONSTRAINT IF EXISTS chk_third_party_additional_account,
    ADD CONSTRAINT chk_third_party_additional_account
        CHECK (additional_account_id IS NULL OR additional_account_id IN ('1', '2')),
    DROP CONSTRAINT IF EXISTS chk_third_party_municipality_code,
    ADD CONSTRAINT chk_third_party_municipality_code
        CHECK (municipality_code IS NULL OR char_length(municipality_code) = 5);

COMMENT ON COLUMN third_parties.dian_identification_type_code IS
    'Código DIAN del tipo de identificación; NIT = 31, consumidor final = 13.';
COMMENT ON COLUMN third_parties.dian_tax_level_code IS
    'Responsabilidades DIAN del adquiriente separadas por punto y coma; consumidor final = R-99-PN.';
COMMENT ON COLUMN third_parties.municipality_code IS
    'Código DANE del municipio de la dirección fiscal (5 dígitos).';
COMMENT ON COLUMN third_parties.registration_name IS
    'Nombre o razón social registrado en el RUT; puede diferir del nombre comercial.';
COMMENT ON COLUMN third_parties.additional_account_id IS
    'Tipo de organización DIAN: 1 persona jurídica, 2 persona natural.';
COMMENT ON COLUMN third_parties.is_consumer_final IS
    'Venta a consumidor final: identificación 222222222222, schemeName 13 y R-99-PN.';

-- Backfill conservador: solo mapeamos NIT, que es inequívoco.
UPDATE third_parties tp
SET dian_identification_type_code = '31'
FROM identification_types it
WHERE tp.identification_type_id = it.id
  AND it.code = 'NIT'
  AND tp.dian_identification_type_code IS NULL;

UPDATE third_parties
SET registration_name = name
WHERE registration_name IS NULL;

UPDATE third_parties
SET additional_account_id = CASE person_type
        WHEN 'JURIDICA' THEN '1'
        WHEN 'NATURAL' THEN '2'
    END
WHERE additional_account_id IS NULL;

-- Completar la dirección fiscal desde los catálogos existentes.
UPDATE third_parties tp
SET municipality_code = COALESCE(tp.municipality_code, c.code),
    city_name = COALESCE(tp.city_name, c.name),
    department_code = COALESCE(tp.department_code, d.code),
    department_name = COALESCE(tp.department_name, d.name)
FROM cities c
LEFT JOIN departments d ON d.id = c.department_id
WHERE tp.city_id = c.id
  AND (
      tp.municipality_code IS NULL
      OR tp.city_name IS NULL
      OR tp.department_code IS NULL
      OR tp.department_name IS NULL
  );

-- Compatibilidad con el campo legado city_code cuando ya es DANE de 5 dígitos.
UPDATE third_parties
SET municipality_code = city_code
WHERE municipality_code IS NULL
  AND city_code IS NOT NULL
  AND char_length(city_code) = 5;

-- ── Índices de consulta fiscal ──────────────────────────────────────────
CREATE INDEX IF NOT EXISTS idx_third_parties_dian_identification
    ON third_parties (dian_identification_type_code);

CREATE INDEX IF NOT EXISTS idx_third_parties_municipality
    ON third_parties (municipality_code);
