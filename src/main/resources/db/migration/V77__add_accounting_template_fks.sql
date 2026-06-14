-- ============================================================
-- V77: Add accounting_template_id FK to product_groups and products
-- Enables template assignment at group and product level
-- ============================================================

ALTER TABLE product_groups
    ADD COLUMN IF NOT EXISTS accounting_template_id UUID REFERENCES accounting_templates(id);

ALTER TABLE products
    ADD COLUMN IF NOT EXISTS accounting_template_id UUID REFERENCES accounting_templates(id);
