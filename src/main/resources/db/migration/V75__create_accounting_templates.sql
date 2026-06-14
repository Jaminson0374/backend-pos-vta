-- ============================================================
-- V75: Create accounting_templates and accounting_template_entries
-- Configurable accounting templates replace hardcoded PUC accounts
-- ============================================================

CREATE TABLE accounting_templates (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    module VARCHAR(20) NOT NULL CHECK (module IN ('SALE','PURCHASE')),
    is_default BOOLEAN NOT NULL DEFAULT false,
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE accounting_template_entries (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    template_id UUID NOT NULL REFERENCES accounting_templates(id) ON DELETE CASCADE,
    event_type VARCHAR(50) NOT NULL,
    account_id UUID NOT NULL REFERENCES puc_accounts(id),
    is_debit BOOLEAN NOT NULL,
    priority INTEGER NOT NULL DEFAULT 0,
    UNIQUE (template_id, event_type)
);

CREATE INDEX idx_ate_template ON accounting_template_entries(template_id);
