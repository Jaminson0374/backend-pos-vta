ALTER TABLE purchase_orders DROP COLUMN IF EXISTS buyer_name;
ALTER TABLE purchase_orders ADD COLUMN IF NOT EXISTS buyer_id UUID REFERENCES third_parties(id);
