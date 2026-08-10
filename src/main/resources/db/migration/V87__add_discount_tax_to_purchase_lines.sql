-- V87: Add discount and tax to purchase line items
ALTER TABLE purchase_line_items
  ADD COLUMN IF NOT EXISTS discount_pct NUMERIC(5,2) DEFAULT 0
    CHECK (discount_pct >= 0 AND discount_pct <= 100),
  ADD COLUMN IF NOT EXISTS tax_type VARCHAR(10) DEFAULT 'EXENTO'
    CHECK (tax_type IN ('EXENTO', 'IVA_5', 'IVA_8', 'IVA_19'));
