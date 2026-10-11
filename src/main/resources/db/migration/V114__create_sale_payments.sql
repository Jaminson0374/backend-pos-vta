-- ============================================================
-- V114: Medios de pago de la venta (Fase 2 — WU-2/C3)
-- Registra cada línea de pago (tender) recibida en un checkout POS,
-- ligada a la factura de venta. Habilita el arqueo y la conciliación
-- contable por medio de pago, que antes se descartaban.
-- ============================================================

CREATE TABLE sale_payments (
    id                UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    sales_document_id UUID           NOT NULL REFERENCES sales_documents(id),
    method            VARCHAR(50)    NOT NULL,
    amount            NUMERIC(15,2)  NOT NULL CHECK (amount >= 0),
    created_by        UUID           REFERENCES users(id),
    created_at        TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    version           BIGINT         NOT NULL DEFAULT 0
);

CREATE INDEX idx_sale_payments_sales_document ON sale_payments(sales_document_id);
