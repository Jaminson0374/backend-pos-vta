ALTER TABLE company_config ADD COLUMN IF NOT EXISTS moratory_interest_rate NUMERIC(5,2) DEFAULT 0;
ALTER TABLE company_config ADD COLUMN IF NOT EXISTS interest_grace_days INT DEFAULT 0;
ALTER TABLE company_config ADD COLUMN IF NOT EXISTS interest_compound_frequency VARCHAR(20) DEFAULT 'MONTHLY';
