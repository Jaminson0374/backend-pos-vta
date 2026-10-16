-- ============================================================
-- V132: Concurrency-safe purchase order numbering (Compras audit A5)
--
-- The old scheme read the highest document_number with a given
-- prefix, parsed it and added one. Under concurrent transactions
-- that read-modify-write produced the SAME document_number for
-- two orders (no lock, no unique constraint). It is replaced by
-- a database sequence, exactly like journal_entry_number_seq
-- (V116), so nextval() is atomic and never collides.
--
-- The UNIQUE constraint is the final DB-level guard: even if an
-- application path tried to insert a duplicate, the write fails
-- loudly instead of silently corrupting OC numbering.
--
-- NOTE: if pre-existing duplicate document_number values are
-- present (possible with the racy scheme), this ALTER TABLE will
-- fail. Deduplicate purchase_orders.document_number before
-- applying this migration in such an environment.
-- ============================================================

CREATE SEQUENCE IF NOT EXISTS purchase_order_number_seq START 1;

ALTER TABLE purchase_orders
    ADD CONSTRAINT uq_purchase_orders_document_number UNIQUE (document_number);
