-- V88: Add purchase retefuente rate to company config
ALTER TABLE company_config
  ADD COLUMN IF NOT EXISTS purchase_retefuente_rate NUMERIC(5,2) DEFAULT 0
    CHECK (purchase_retefuente_rate >= 0 AND purchase_retefuente_rate <= 100);
