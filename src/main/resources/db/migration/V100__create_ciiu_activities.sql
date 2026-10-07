-- ============================================================
-- V100: Crea la tabla de actividades económicas CIIU (DIAN).
-- ============================================================

CREATE TABLE IF NOT EXISTS ciiu_activities (
    id UUID PRIMARY KEY,
    code VARCHAR(10) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE
);
