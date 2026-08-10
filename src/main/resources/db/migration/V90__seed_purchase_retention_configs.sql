-- V90: Seed purchase_retention_configs with standard Colombian retentions
INSERT INTO purchase_retention_configs (code, name, description, rate, base_min, applies_to_tax_regime, applies_to_person_type, active, sort_order) VALUES
('RENTA_COMPRAS_BIENES', 'Retención en la fuente - Compras de bienes', 'Retención por renta aplicable a compras de bienes muebles', 2.50, 0, 'COMUN', NULL, true, 1),
('RENTA_SERVICIOS_GENERAL', 'Retención en la fuente - Servicios (general)', 'Retención por renta aplicable a servicios en general', 4.00, 0, 'COMUN', NULL, true, 2),
('RENTA_SERVICIOS_ESPECIAL', 'Retención en la fuente - Servicios (especial)', 'Retención por renta aplicable a servicios especiales (transporte, aseo, vigilancia)', 6.00, 0, 'COMUN', NULL, true, 3),
('RETEIVA_COMPRAS', 'Retención de IVA - Compras', 'Retención del 15% del IVA en compras a responsables del régimen común', 15.00, 0, 'COMUN', NULL, true, 4),
('RETEICA_COMPRAS', 'Retención de ICA - Compras', 'Retención de Industria y Comercio aplicable a compras según municipio', 0.50, 0, 'COMUN', NULL, true, 5)
ON CONFLICT (code) DO NOTHING;
