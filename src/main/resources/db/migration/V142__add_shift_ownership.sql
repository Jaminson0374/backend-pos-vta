ALTER TABLE shifts ADD COLUMN assigned_to UUID REFERENCES users(id);
ALTER TABLE shifts ADD COLUMN closed_by UUID REFERENCES users(id);
CREATE INDEX idx_shifts_assigned_to ON shifts(assigned_to);
