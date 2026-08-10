-- =====================================================================
-- V93: Add audit columns (created_by, updated_by, updated_at)
-- to user-facing tables. Only tables verified from existing migrations.
-- =====================================================================

-- ── MASTER DATA (V1-V8) ──────────────────────────────────────────────────
ALTER TABLE roles                ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW();
ALTER TABLE roles                ADD COLUMN IF NOT EXISTS created_by UUID REFERENCES users(id);
ALTER TABLE roles                ADD COLUMN IF NOT EXISTS updated_by UUID REFERENCES users(id);

ALTER TABLE warehouses           ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW();
ALTER TABLE warehouses           ADD COLUMN IF NOT EXISTS created_by UUID REFERENCES users(id);
ALTER TABLE warehouses           ADD COLUMN IF NOT EXISTS updated_by UUID REFERENCES users(id);

ALTER TABLE cash_registers       ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW();
ALTER TABLE cash_registers       ADD COLUMN IF NOT EXISTS created_by UUID REFERENCES users(id);
ALTER TABLE cash_registers       ADD COLUMN IF NOT EXISTS updated_by UUID REFERENCES users(id);

ALTER TABLE products             ADD COLUMN IF NOT EXISTS created_by UUID REFERENCES users(id);
ALTER TABLE products             ADD COLUMN IF NOT EXISTS updated_by UUID REFERENCES users(id);

ALTER TABLE third_parties        ADD COLUMN IF NOT EXISTS created_by UUID REFERENCES users(id);
ALTER TABLE third_parties        ADD COLUMN IF NOT EXISTS updated_by UUID REFERENCES users(id);

-- ── CATALOGS (V12, V16, V17) ─────────────────────────────────────────────
ALTER TABLE third_party_categories ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT NOW();
ALTER TABLE third_party_categories ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW();
ALTER TABLE third_party_categories ADD COLUMN IF NOT EXISTS created_by UUID REFERENCES users(id);
ALTER TABLE third_party_categories ADD COLUMN IF NOT EXISTS updated_by UUID REFERENCES users(id);

ALTER TABLE identification_types ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW();
ALTER TABLE identification_types ADD COLUMN IF NOT EXISTS created_by UUID REFERENCES users(id);
ALTER TABLE identification_types ADD COLUMN IF NOT EXISTS updated_by UUID REFERENCES users(id);

ALTER TABLE product_types        ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW();
ALTER TABLE product_types        ADD COLUMN IF NOT EXISTS created_by UUID REFERENCES users(id);
ALTER TABLE product_types        ADD COLUMN IF NOT EXISTS updated_by UUID REFERENCES users(id);

ALTER TABLE product_states       ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW();
ALTER TABLE product_states       ADD COLUMN IF NOT EXISTS created_by UUID REFERENCES users(id);
ALTER TABLE product_states       ADD COLUMN IF NOT EXISTS updated_by UUID REFERENCES users(id);

ALTER TABLE brands               ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW();
ALTER TABLE brands               ADD COLUMN IF NOT EXISTS created_by UUID REFERENCES users(id);
ALTER TABLE brands               ADD COLUMN IF NOT EXISTS updated_by UUID REFERENCES users(id);

ALTER TABLE product_models       ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW();
ALTER TABLE product_models       ADD COLUMN IF NOT EXISTS created_by UUID REFERENCES users(id);
ALTER TABLE product_models       ADD COLUMN IF NOT EXISTS updated_by UUID REFERENCES users(id);

ALTER TABLE product_categories   ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW();
ALTER TABLE product_categories   ADD COLUMN IF NOT EXISTS created_by UUID REFERENCES users(id);
ALTER TABLE product_categories   ADD COLUMN IF NOT EXISTS updated_by UUID REFERENCES users(id);

ALTER TABLE product_groups       ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW();
ALTER TABLE product_groups       ADD COLUMN IF NOT EXISTS created_by UUID REFERENCES users(id);
ALTER TABLE product_groups       ADD COLUMN IF NOT EXISTS updated_by UUID REFERENCES users(id);

ALTER TABLE units_of_measure     ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW();
ALTER TABLE units_of_measure     ADD COLUMN IF NOT EXISTS created_by UUID REFERENCES users(id);
ALTER TABLE units_of_measure     ADD COLUMN IF NOT EXISTS updated_by UUID REFERENCES users(id);

ALTER TABLE warehouse_locations  ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW();
ALTER TABLE warehouse_locations  ADD COLUMN IF NOT EXISTS created_by UUID REFERENCES users(id);
ALTER TABLE warehouse_locations  ADD COLUMN IF NOT EXISTS updated_by UUID REFERENCES users(id);

ALTER TABLE puc_accounts         ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW();
ALTER TABLE puc_accounts         ADD COLUMN IF NOT EXISTS created_by UUID REFERENCES users(id);
ALTER TABLE puc_accounts         ADD COLUMN IF NOT EXISTS updated_by UUID REFERENCES users(id);

ALTER TABLE price_lists          ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW();
ALTER TABLE price_lists          ADD COLUMN IF NOT EXISTS created_by UUID REFERENCES users(id);
ALTER TABLE price_lists          ADD COLUMN IF NOT EXISTS updated_by UUID REFERENCES users(id);

-- ── TRANSACTIONAL (V25-V57) ──────────────────────────────────────────────
ALTER TABLE batches              ADD COLUMN IF NOT EXISTS updated_by UUID REFERENCES users(id);

ALTER TABLE sales_documents      ADD COLUMN IF NOT EXISTS updated_by UUID REFERENCES users(id);

ALTER TABLE purchase_orders      ADD COLUMN IF NOT EXISTS updated_by UUID REFERENCES users(id);

ALTER TABLE goods_receipts       ADD COLUMN IF NOT EXISTS updated_by UUID REFERENCES users(id);

ALTER TABLE supplier_invoices    ADD COLUMN IF NOT EXISTS updated_by UUID REFERENCES users(id);

ALTER TABLE payments             ADD COLUMN IF NOT EXISTS updated_by UUID REFERENCES users(id);

ALTER TABLE stock_adjustments    ADD COLUMN IF NOT EXISTS updated_by UUID REFERENCES users(id);

ALTER TABLE stock_transfers      ADD COLUMN IF NOT EXISTS updated_by UUID REFERENCES users(id);

ALTER TABLE stock_disposals      ADD COLUMN IF NOT EXISTS updated_by UUID REFERENCES users(id);

ALTER TABLE inventory_movements  ADD COLUMN IF NOT EXISTS updated_by UUID REFERENCES users(id);

ALTER TABLE production_orders    ADD COLUMN IF NOT EXISTS updated_by UUID REFERENCES users(id);

ALTER TABLE purchase_returns     ADD COLUMN IF NOT EXISTS updated_by UUID REFERENCES users(id);

ALTER TABLE customer_receipts    ADD COLUMN IF NOT EXISTS updated_by UUID REFERENCES users(id);

ALTER TABLE debit_credit_notes   ADD COLUMN IF NOT EXISTS updated_by UUID REFERENCES users(id);

ALTER TABLE shifts               ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW();

-- ── ACCOUNTING (V72, V75) ────────────────────────────────────────────────
ALTER TABLE journal_entries      ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW();
ALTER TABLE journal_entries      ADD COLUMN IF NOT EXISTS created_by UUID REFERENCES users(id);
ALTER TABLE journal_entries      ADD COLUMN IF NOT EXISTS updated_by UUID REFERENCES users(id);

ALTER TABLE accounting_templates ADD COLUMN IF NOT EXISTS created_by UUID REFERENCES users(id);
ALTER TABLE accounting_templates ADD COLUMN IF NOT EXISTS updated_by UUID REFERENCES users(id);

-- ── COMPANY CONFIG (V51) ─────────────────────────────────────────────────
ALTER TABLE company_config       ADD COLUMN IF NOT EXISTS created_by UUID REFERENCES users(id);
ALTER TABLE company_config       ADD COLUMN IF NOT EXISTS updated_by UUID REFERENCES users(id);

-- ── PURCHASE RETENTION (V89) ─────────────────────────────────────────────
ALTER TABLE purchase_retention_configs ADD COLUMN IF NOT EXISTS created_by UUID REFERENCES users(id);
ALTER TABLE purchase_retention_configs ADD COLUMN IF NOT EXISTS updated_by UUID REFERENCES users(id);

-- ── PRODUCTION (V58-V61, V68) ────────────────────────────────────────────
ALTER TABLE product_formulas     ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW();
ALTER TABLE product_formulas     ADD COLUMN IF NOT EXISTS created_by UUID REFERENCES users(id);
ALTER TABLE product_formulas     ADD COLUMN IF NOT EXISTS updated_by UUID REFERENCES users(id);

ALTER TABLE product_presentations ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW();
ALTER TABLE product_presentations ADD COLUMN IF NOT EXISTS created_by UUID REFERENCES users(id);
ALTER TABLE product_presentations ADD COLUMN IF NOT EXISTS updated_by UUID REFERENCES users(id);

ALTER TABLE production_batches   ADD COLUMN IF NOT EXISTS updated_by UUID REFERENCES users(id);

ALTER TABLE machinery            ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW();
ALTER TABLE machinery            ADD COLUMN IF NOT EXISTS created_by UUID REFERENCES users(id);
ALTER TABLE machinery            ADD COLUMN IF NOT EXISTS updated_by UUID REFERENCES users(id);

-- ── ANIMALS (V23-V24) ────────────────────────────────────────────────────
ALTER TABLE animals              ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW();
ALTER TABLE animals              ADD COLUMN IF NOT EXISTS created_by UUID REFERENCES users(id);
ALTER TABLE animals              ADD COLUMN IF NOT EXISTS updated_by UUID REFERENCES users(id);

ALTER TABLE slaughters           ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW();
ALTER TABLE slaughters           ADD COLUMN IF NOT EXISTS created_by UUID REFERENCES users(id);
ALTER TABLE slaughters           ADD COLUMN IF NOT EXISTS updated_by UUID REFERENCES users(id);
