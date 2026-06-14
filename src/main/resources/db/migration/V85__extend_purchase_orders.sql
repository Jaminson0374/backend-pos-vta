ALTER TABLE purchase_orders ADD COLUMN IF NOT EXISTS due_date DATE;
ALTER TABLE purchase_orders ADD COLUMN IF NOT EXISTS buyer_name VARCHAR(200);
ALTER TABLE purchase_orders ADD COLUMN IF NOT EXISTS payment_method VARCHAR(50);
ALTER TABLE purchase_orders ADD COLUMN IF NOT EXISTS support_document_type VARCHAR(50);
ALTER TABLE purchase_orders ADD COLUMN IF NOT EXISTS support_document_number VARCHAR(50);
ALTER TABLE purchase_orders ADD COLUMN IF NOT EXISTS currency VARCHAR(10) DEFAULT 'COP';
