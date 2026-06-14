CREATE TABLE purchase_returns (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    receipt_id      UUID         NOT NULL REFERENCES goods_receipts(id),
    return_date     DATE         NOT NULL DEFAULT CURRENT_DATE,
    document_number VARCHAR(30),
    reason          TEXT,
    status          VARCHAR(20)  NOT NULL DEFAULT 'COMPLETED'
                    CHECK (status IN ('COMPLETED', 'CANCELLED')),
    created_by      UUID         NOT NULL REFERENCES users(id),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    version         BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uq_purchase_return_receipt UNIQUE (receipt_id)
);

CREATE TABLE purchase_return_lines (
    id           UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    return_id    UUID           NOT NULL REFERENCES purchase_returns(id) ON DELETE CASCADE,
    product_id   UUID           NOT NULL REFERENCES products(id),
    warehouse_id UUID           NOT NULL REFERENCES warehouses(id),
    batch_id     UUID           REFERENCES batches(id),
    return_qty   NUMERIC(15,3)  NOT NULL CHECK (return_qty > 0),
    unit_cost    NUMERIC(15,2)  NOT NULL CHECK (unit_cost >= 0),
    line_number  INT            NOT NULL DEFAULT 0
);

CREATE INDEX idx_pr_receipt ON purchase_returns(receipt_id);
CREATE INDEX idx_pr_status ON purchase_returns(status);
CREATE INDEX idx_prl_return ON purchase_return_lines(return_id);
