-- ============================================================
-- V138: Lista negra de JWT revocados (S2b)
--
-- Permite revocar un token individual (logout) sin esperar su
-- expiración. Cada JWT lleva un jti único; al cerrar sesión se
-- registra ese jti junto con su expiración y el filtro lo rechaza
-- en las siguientes peticiones. Las filas vencidas se purgan
-- periódicamente.
-- ============================================================

CREATE TABLE jwt_blacklist (
    jti UUID PRIMARY KEY,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_jwt_blacklist_expires_at ON jwt_blacklist(expires_at);
