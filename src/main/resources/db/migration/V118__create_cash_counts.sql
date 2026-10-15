-- ============================================================
-- V118: Arqueo de caja — registro histórico del conteo por denominación
-- El POS contaba las denominaciones en memoria y las descartaba al cerrar el
-- arqueo. Estas tablas conservan cada conteo (cabecera + líneas) ligado al turno
-- para auditoría y conciliación posterior.
-- ============================================================

CREATE TABLE cash_counts (
    id             UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    shift_id       UUID           NOT NULL REFERENCES shifts(id),
    expected_total NUMERIC(19,2),
    counted_total  NUMERIC(19,2)  NOT NULL,
    difference     NUMERIC(19,2),
    notes          TEXT,
    created_by     UUID           REFERENCES users(id),
    created_at     TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    version        BIGINT         NOT NULL DEFAULT 0
);

CREATE INDEX idx_cash_counts_shift ON cash_counts(shift_id);

CREATE TABLE cash_count_lines (
    id                 UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    cash_count_id      UUID           NOT NULL REFERENCES cash_counts(id) ON DELETE CASCADE,
    denomination_value NUMERIC(19,2)  NOT NULL,
    denomination_type  VARCHAR(20)    NOT NULL,
    quantity           INT            NOT NULL,
    subtotal           NUMERIC(19,2)  NOT NULL
);

CREATE INDEX idx_cash_count_lines_cash_count ON cash_count_lines(cash_count_id);
