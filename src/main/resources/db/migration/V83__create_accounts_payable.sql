CREATE TABLE accounts_payable (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    supplier_id UUID NOT NULL REFERENCES third_parties(id),
    document_id UUID REFERENCES supplier_invoices(id),
    debit_credit_note_id UUID REFERENCES debit_credit_notes(id),
    total_amount NUMERIC(15,2) NOT NULL,
    paid_amount NUMERIC(15,2) NOT NULL DEFAULT 0,
    outstanding NUMERIC(15,2) NOT NULL DEFAULT 0,
    due_date DATE NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ap_status_check CHECK (status IN ('OPEN','PARTIAL','PAID','OVERDUE'))
);

CREATE INDEX idx_ap_supplier ON accounts_payable(supplier_id);
CREATE INDEX idx_ap_status ON accounts_payable(status);
CREATE INDEX idx_ap_due_date ON accounts_payable(due_date);
