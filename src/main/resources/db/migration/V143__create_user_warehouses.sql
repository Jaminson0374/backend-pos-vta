CREATE TABLE user_warehouses (
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    warehouse_id UUID NOT NULL REFERENCES warehouses(id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, warehouse_id)
);
CREATE INDEX idx_user_warehouses_warehouse ON user_warehouses(warehouse_id);
