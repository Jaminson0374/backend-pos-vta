-- V108 — Normalize company_config.costing_method into canonical stock costing strategies.
-- The column already exists (V62, default 'WEIGHTED_AVERAGE'); this migration only
-- normalizes legacy values into the strategy set {FIFO, WEIGHTED_AVG}.
-- Idempotent: each UPDATE becomes a no-op once its source value no longer exists.

-- PEPS (Spanish for FIFO) → FIFO
UPDATE company_config SET costing_method = 'FIFO' WHERE costing_method = 'PEPS';

-- PROMEDIO_PONDERADO (Spanish weighted average) → WEIGHTED_AVG
UPDATE company_config SET costing_method = 'WEIGHTED_AVG' WHERE costing_method = 'PROMEDIO_PONDERADO';

-- Legacy English weighted average → WEIGHTED_AVG
UPDATE company_config SET costing_method = 'WEIGHTED_AVG' WHERE costing_method = 'WEIGHTED_AVERAGE';

-- ESTANDAR (standard) is unimplemented → FIFO layers approximate it
UPDATE company_config SET costing_method = 'FIFO' WHERE costing_method = 'ESTANDAR';

-- IDENTIFICACION_ESPECIFICA (specific identification) is unimplemented → FIFO
UPDATE company_config SET costing_method = 'FIFO' WHERE costing_method = 'IDENTIFICACION_ESPECIFICA';

-- YIELD_COSTING is the Desposte transformation, not a stock selector → exclude/blank.
-- The selector (CostingMethod.fromString) falls back to FIFO for null/blank/unknown.
UPDATE company_config SET costing_method = NULL WHERE costing_method = 'YIELD_COSTING';
