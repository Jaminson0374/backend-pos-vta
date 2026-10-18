-- ============================================================
-- V137: Versión de token por usuario (S2a)
--
-- Permite invalidar todos los JWT emitidos para un usuario sin
-- esperar su expiración. Cada token lleva el token_version con el
-- que fue emitido; si el usuario cambia de contraseña o se
-- desactiva, la versión se incrementa y los tokens viejos dejan
-- de autenticar.
-- ============================================================

ALTER TABLE users ADD COLUMN token_version BIGINT NOT NULL DEFAULT 0;
