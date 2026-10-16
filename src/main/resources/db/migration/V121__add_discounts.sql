-- ============================================================
-- V121: Descuentos por línea y por documento (M2 / WU-26)
--
-- El descuento se aplica SIEMPRE ANTES del IVA (reduce la base
-- gravable), tal como exige la DIAN. Se registra el descuento de
-- línea (automático por promoción + manual) y el de documento,
-- junto con su autorización y motivo (auditoría).
-- ============================================================

ALTER TABLE sales_items
  ADD COLUMN discount_type   VARCHAR(10) NOT NULL DEFAULT 'NONE',
  ADD COLUMN discount_value  NUMERIC(19,2) NOT NULL DEFAULT 0,
  ADD COLUMN discount_amount NUMERIC(19,2) NOT NULL DEFAULT 0,
  ADD COLUMN promotion_id    UUID REFERENCES product_promotions(id),
  ADD CONSTRAINT chk_si_discount_type CHECK (discount_type IN ('NONE','PCT','AMOUNT'));

ALTER TABLE sales_documents
  ADD COLUMN discount_total          NUMERIC(19,2) NOT NULL DEFAULT 0,
  ADD COLUMN document_discount       NUMERIC(19,2) NOT NULL DEFAULT 0,
  ADD COLUMN document_discount_type  VARCHAR(10)   NOT NULL DEFAULT 'NONE',
  ADD COLUMN document_discount_value NUMERIC(19,2) NOT NULL DEFAULT 0,
  ADD COLUMN discount_authorized_by  UUID REFERENCES users(id),
  ADD COLUMN discount_reason         VARCHAR(255),
  ADD CONSTRAINT chk_sd_document_discount_type CHECK (document_discount_type IN ('NONE','PCT','AMOUNT'));

ALTER TABLE company_config
  ADD COLUMN max_discount_pct         NUMERIC(5,2) NOT NULL DEFAULT 30,
  ADD COLUMN cashier_max_discount_pct NUMERIC(5,2) NOT NULL DEFAULT 5;

CREATE INDEX idx_sales_documents_discount ON sales_documents(discount_total);
