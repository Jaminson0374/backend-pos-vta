INSERT INTO roles (name, permissions) VALUES
    ('ALMACENISTA', '["inventory.*","production.*","logistics.*"]')
ON CONFLICT (name) DO NOTHING;
