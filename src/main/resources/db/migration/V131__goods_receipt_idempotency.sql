-- ============================================================
-- V131: Goods-receipt idempotency key (Compras WU-41 — hallazgos C3 + A8)
--
-- A retried receipt submission must not register the same physical receipt twice. The
-- optional idempotency_key carries the caller's retry token; a partial unique index
-- enforces one receipt per key while leaving keyless (manual) receipts unrestricted,
-- since multiple NULLs are allowed by a partial index.
-- ============================================================

ALTER TABLE goods_receipts ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(100);

CREATE UNIQUE INDEX IF NOT EXISTS uq_goods_receipts_idempotency_key
    ON goods_receipts (idempotency_key)
    WHERE idempotency_key IS NOT NULL;
