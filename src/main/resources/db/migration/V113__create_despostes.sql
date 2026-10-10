-- =====================================================================
-- V113: Persist manual desposte (MVM) header and resulting cuts.
--   - despostes: one row per executed manual desposte with its mass balance,
--     totals and yield.
--   - desposte_cuts: one row per resulting cut, linked to the desposte and to
--     the child batch minted for it.
-- =====================================================================

CREATE TABLE despostes (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    source_batch_id        UUID          NOT NULL,
    product_id             UUID          NOT NULL,
    warehouse_id           UUID          NOT NULL,
    input_weight           NUMERIC(19,6),
    total_cuts_weight      NUMERIC(19,6),
    waste_weight           NUMERIC(19,6),
    shrink_weight          NUMERIC(19,6),
    deviation              NUMERIC(19,6),
    tolerance              NUMERIC(19,6),
    within_tolerance       BOOLEAN,
    yield_percentage       NUMERIC(9,4),
    total_commercial_value NUMERIC(19,6),
    total_allocated_cost   NUMERIC(19,6),
    notes                  TEXT,
    created_by             VARCHAR(150),
    created_at             TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_despostes_source_batch_id ON despostes(source_batch_id);
CREATE INDEX idx_despostes_created_at      ON despostes(created_at);

CREATE TABLE desposte_cuts (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    desposte_id          UUID NOT NULL REFERENCES despostes(id),
    product_id           UUID NOT NULL,
    warehouse_id         UUID NOT NULL,
    child_batch_id       UUID,
    weight               NUMERIC(19,6),
    suggested_sale_price NUMERIC(19,6),
    commercial_value     NUMERIC(19,6),
    allocated_cost       NUMERIC(19,6),
    unit_cost            NUMERIC(19,6),
    expiration_date      DATE
);

CREATE INDEX idx_desposte_cuts_desposte_id ON desposte_cuts(desposte_id);
