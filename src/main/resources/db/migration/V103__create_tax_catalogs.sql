-- ============================================================
-- V103: Catálogos tributarios (régimen, responsabilidades, impuestos).
-- ============================================================

CREATE TABLE IF NOT EXISTS tax_responsibilities (
    id         UUID PRIMARY KEY,
    code       VARCHAR(50)  NOT NULL UNIQUE,
    name       VARCHAR(100) NOT NULL,
    excludes   JSONB        NOT NULL DEFAULT '[]',
    sort_order INT          NOT NULL DEFAULT 0,
    active     BOOLEAN      NOT NULL DEFAULT TRUE
);

CREATE TABLE IF NOT EXISTS fiscal_responsibilities (
    id         UUID PRIMARY KEY,
    code       VARCHAR(50)  NOT NULL UNIQUE,
    name       VARCHAR(100) NOT NULL,
    excludes   JSONB        NOT NULL DEFAULT '[]',
    sort_order INT          NOT NULL DEFAULT 0,
    active     BOOLEAN      NOT NULL DEFAULT TRUE
);

CREATE TABLE IF NOT EXISTS taxes (
    id         UUID PRIMARY KEY,
    code       VARCHAR(50)  NOT NULL UNIQUE,
    name       VARCHAR(100) NOT NULL,
    sort_order INT          NOT NULL DEFAULT 0,
    active     BOOLEAN      NOT NULL DEFAULT TRUE
);
