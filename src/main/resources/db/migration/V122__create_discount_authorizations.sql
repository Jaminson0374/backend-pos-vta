-- ============================================================
-- V122: Tokens de autorización de descuentos (M2 / WU-27)
--
-- Un ADMIN aprueba un descuento que excede la política (techo del
-- cajero o techo global) y emite un token de UN SOLO USO. El checkout
-- solo lleva el id del token: las credenciales del administrador nunca
-- viajan en la petición de facturación.
-- ============================================================

CREATE TABLE discount_authorizations (
    id               UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    requested_by     UUID        REFERENCES users(id),
    authorized_by    UUID        NOT NULL REFERENCES users(id),
    cash_register_id UUID        REFERENCES cash_registers(id),
    expires_at       TIMESTAMPTZ NOT NULL,
    used             BOOLEAN     NOT NULL DEFAULT false,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_discount_auth_expires ON discount_authorizations(expires_at);
