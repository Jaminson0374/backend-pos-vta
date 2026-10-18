-- El JSONB de permisos nunca se aplicó en la autorización; los roles son ahora un catálogo estático.
ALTER TABLE roles DROP COLUMN permissions;
