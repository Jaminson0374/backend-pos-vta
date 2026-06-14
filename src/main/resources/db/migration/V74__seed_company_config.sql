INSERT INTO company_config (id, company_name, nit)
VALUES (1, 'Mi Empresa', '000000000-0')
ON CONFLICT (id) DO NOTHING;
