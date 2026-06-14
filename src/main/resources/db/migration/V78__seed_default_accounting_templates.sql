-- ============================================================
-- V78: Seed default accounting templates
-- Provides fallback templates that mirror current hardcoded PUC behavior
-- Only inserts entries for PUC accounts that actually exist
-- ============================================================

-- ── DEFAULT_SALE ──
INSERT INTO accounting_templates (id, code, name, description, module, is_default, is_active)
VALUES (gen_random_uuid(), 'DEFAULT_SALE', 'Venta por defecto', 'Plantilla contable predeterminada para ventas. Replica el comportamiento actual con cuentas PUC fijas.', 'SALE', true, true);

-- SALE_RECEIVABLE: 1305 Clientes (DEBITO)
INSERT INTO accounting_template_entries (template_id, event_type, account_id, is_debit, priority)
SELECT t.id, 'SALE_RECEIVABLE', a.id, true, 1
FROM accounting_templates t, puc_accounts a
WHERE t.code = 'DEFAULT_SALE' AND a.code = '1305';

-- SALE_INCOME: 4135 Comercio al por menor (CREDITO)
INSERT INTO accounting_template_entries (template_id, event_type, account_id, is_debit, priority)
SELECT t.id, 'SALE_INCOME', a.id, false, 2
FROM accounting_templates t, puc_accounts a
WHERE t.code = 'DEFAULT_SALE' AND a.code = '4135';

-- SALE_COGS: 6135 Costo de ventas (DEBITO)
INSERT INTO accounting_template_entries (template_id, event_type, account_id, is_debit, priority)
SELECT t.id, 'SALE_COGS', a.id, true, 3
FROM accounting_templates t, puc_accounts a
WHERE t.code = 'DEFAULT_SALE' AND a.code = '6135';

-- SALE_INVENTORY_OUT: 1435 Mercancías no fabricadas (CREDITO)
INSERT INTO accounting_template_entries (template_id, event_type, account_id, is_debit, priority)
SELECT t.id, 'SALE_INVENTORY_OUT', a.id, false, 4
FROM accounting_templates t, puc_accounts a
WHERE t.code = 'DEFAULT_SALE' AND a.code = '1435';

-- SALE_TAX: 2408 IVA por pagar (CREDITO)
INSERT INTO accounting_template_entries (template_id, event_type, account_id, is_debit, priority)
SELECT t.id, 'SALE_TAX', a.id, false, 5
FROM accounting_templates t, puc_accounts a
WHERE t.code = 'DEFAULT_SALE' AND a.code = '2408';

-- SALE_RETENTION: 2365 Retención en la fuente (CREDITO)
INSERT INTO accounting_template_entries (template_id, event_type, account_id, is_debit, priority)
SELECT t.id, 'SALE_RETENTION', a.id, false, 6
FROM accounting_templates t, puc_accounts a
WHERE t.code = 'DEFAULT_SALE' AND a.code = '2365';

-- ── DEFAULT_PURCHASE ──
INSERT INTO accounting_templates (id, code, name, description, module, is_default, is_active)
VALUES (gen_random_uuid(), 'DEFAULT_PURCHASE', 'Compra por defecto', 'Plantilla contable predeterminada para compras. Replica el comportamiento actual con cuentas PUC fijas.', 'PURCHASE', true, true);

-- PURCHASE_INVENTORY: 1435 Mercancías no fabricadas (DEBITO)
INSERT INTO accounting_template_entries (template_id, event_type, account_id, is_debit, priority)
SELECT t.id, 'PURCHASE_INVENTORY', a.id, true, 1
FROM accounting_templates t, puc_accounts a
WHERE t.code = 'DEFAULT_PURCHASE' AND a.code = '1435';

-- PURCHASE_PAYABLE: 2205 Proveedores nacionales (CREDITO)
INSERT INTO accounting_template_entries (template_id, event_type, account_id, is_debit, priority)
SELECT t.id, 'PURCHASE_PAYABLE', a.id, false, 2
FROM accounting_templates t, puc_accounts a
WHERE t.code = 'DEFAULT_PURCHASE' AND a.code = '2205';

-- PURCHASE_TAX: 240820 IVA descontable (DEBITO)
INSERT INTO accounting_template_entries (template_id, event_type, account_id, is_debit, priority)
SELECT t.id, 'PURCHASE_TAX', a.id, true, 3
FROM accounting_templates t, puc_accounts a
WHERE t.code = 'DEFAULT_PURCHASE' AND a.code = '240820';

-- PURCHASE_RETENTION: 2365 Retención en la fuente (CREDITO)
INSERT INTO accounting_template_entries (template_id, event_type, account_id, is_debit, priority)
SELECT t.id, 'PURCHASE_RETENTION', a.id, false, 4
FROM accounting_templates t, puc_accounts a
WHERE t.code = 'DEFAULT_PURCHASE' AND a.code = '2365';

-- PURCHASE_DISCOUNT: 4210 Descuentos en compras (CREDITO)
INSERT INTO accounting_template_entries (template_id, event_type, account_id, is_debit, priority)
SELECT t.id, 'PURCHASE_DISCOUNT', a.id, false, 5
FROM accounting_templates t, puc_accounts a
WHERE t.code = 'DEFAULT_PURCHASE' AND a.code = '4210';
