-- S4: los tokens de restablecimiento de contraseña se guardaban en texto plano.
-- A partir de esta versión solo se persiste su hash SHA-256, por lo que ningún
-- token anterior podrá volver a coincidir con la nueva búsqueda. Se eliminan
-- todos los tokens existentes para invalidarlos de forma definitiva.
DELETE FROM password_reset_tokens;
