-- ============================================================
-- V119: Sembrar el rol VENDEDOR
-- Hallazgo M8: el rol VENDEDOR no existía como rol sembrado, aunque un test
-- de autorización (PosControllerDevolutionsAuthorizationTest) lo espera.
-- Se agrega al catálogo con permisos de lectura/cotización (los permisos JSONB
-- son informativos; la autorización real es por rol en @PreAuthorize).
-- ============================================================

INSERT INTO roles (name, permissions) VALUES
    ('VENDEDOR', '["sales.read","sales.quote"]')
ON CONFLICT (name) DO NOTHING;
