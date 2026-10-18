-- ============================================================
-- V139: Endurecimiento de certificados digitales (S3)
--
-- El campo password_encrypted almacenaba la contraseña del
-- keystore en Base64, un encoding reversible: cualquiera con
-- lectura de la base de datos podía recuperarla. Todos los
-- certificados heredados deben considerarse comprometidos y se
-- eliminan; deben volver a cargarse cifrados con AES-256-GCM.
--
-- También se agregan metadatos (issuer/subject) para que la API
-- administrativa exponga solo información no sensible.
-- ============================================================

ALTER TABLE digital_certificates ADD COLUMN IF NOT EXISTS issuer VARCHAR(255);
ALTER TABLE digital_certificates ADD COLUMN IF NOT EXISTS subject VARCHAR(255);

UPDATE company_config SET certificate_id = NULL WHERE certificate_id IS NOT NULL;

DELETE FROM digital_certificates;
