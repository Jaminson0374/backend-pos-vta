-- ============================================================
-- V104: Seed de catálogos tributarios.
-- ============================================================

INSERT INTO tax_responsibilities (id, code, name, excludes, sort_order) VALUES
(gen_random_uuid(), 'IVA_RESPONSABLE',    'Responsable de IVA',                 '["IVA_NO_RESPONSABLE","IVA_INC"]', 1),
(gen_random_uuid(), 'IVA_NO_RESPONSABLE', 'No responsable de IVA',              '["IVA_RESPONSABLE","IVA_INC"]', 2),
(gen_random_uuid(), 'INC_RESPONSABLE',    'Impuesto Nacional al Consumo (INC)', '["INC_NO_RESPONSABLE","IVA_INC"]', 3),
(gen_random_uuid(), 'INC_NO_RESPONSABLE', 'No responsable de INC',              '["INC_RESPONSABLE","IVA_INC"]', 4),
(gen_random_uuid(), 'IVA_INC',            'Responsable de IVA e INC',           '["IVA_RESPONSABLE","IVA_NO_RESPONSABLE","INC_RESPONSABLE","INC_NO_RESPONSABLE"]', 5),
(gen_random_uuid(), 'REGIMEN_ESPECIAL',   'Régimen especial',                   '[]', 6);

INSERT INTO fiscal_responsibilities (id, code, name, excludes, sort_order) VALUES
(gen_random_uuid(), 'GRAN_CONTRIBUYENTE',   'Gran contribuyente',            '["REGIMEN_SIMPLE","NO_APLICA"]', 1),
(gen_random_uuid(), 'REGIMEN_SIMPLE',       'Régimen simple de tributación', '["GRAN_CONTRIBUYENTE","NO_APLICA"]', 2),
(gen_random_uuid(), 'AUTORRETENEDOR',       'Autorretenedor',                '["NO_APLICA"]', 3),
(gen_random_uuid(), 'AGENTE_RETENCION_IVA', 'Agente de retención IVA',       '["NO_APLICA"]', 4),
(gen_random_uuid(), 'NO_APLICA',            'No aplica',                     '["GRAN_CONTRIBUYENTE","REGIMEN_SIMPLE","AUTORRETENEDOR","AGENTE_RETENCION_IVA"]', 5);

INSERT INTO taxes (id, code, name, sort_order) VALUES
(gen_random_uuid(), 'IVA',         'IVA', 1),
(gen_random_uuid(), 'RETE_IVA',    'Rete IVA', 2),
(gen_random_uuid(), 'INC',         'Impuesto Nacional al Consumo (INC)', 3),
(gen_random_uuid(), 'RETE_FUENTE', 'Retefuente', 4),
(gen_random_uuid(), 'ICA',         'ICA', 5);
