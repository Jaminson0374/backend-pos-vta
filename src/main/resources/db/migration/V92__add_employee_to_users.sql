ALTER TABLE users ADD COLUMN employee_id UUID REFERENCES third_parties(id);

-- Employee must be a valid, active EMPLOYEE-type third party
-- (validation is handled in application layer)
