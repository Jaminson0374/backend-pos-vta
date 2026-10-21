-- ============================================================
-- V145: D2.1 — Estado de sincronización DIAN con el proveedor.
--
-- `electronic_invoices` sigue siendo la fuente canónica del estado
-- devuelto por Siigo. Las columnas `dian_*` de `sales_documents` son
-- una proyección de lectura para listados/UI; el job de envío debe
-- actualizar ambas en la misma transacción.
-- ============================================================

-- ── Fuente canónica: electronic_invoices ────────────────────────────────
ALTER TABLE electronic_invoices
    ADD COLUMN IF NOT EXISTS provider_invoice_id VARCHAR(100),
    ADD COLUMN IF NOT EXISTS pdf_url TEXT,
    ADD COLUMN IF NOT EXISTS xml_url TEXT;

COMMENT ON COLUMN electronic_invoices.provider_invoice_id IS
    'ID del documento en Siigo; no es el consecutivo fiscal.';
COMMENT ON COLUMN electronic_invoices.pdf_url IS
    'URL oficial de la representación gráfica devuelta por el proveedor.';
COMMENT ON COLUMN electronic_invoices.xml_url IS
    'URL oficial del XML/UBL devuelto por el proveedor.';

-- Recuperar el ID del proveedor desde la respuesta JSON histórica.
UPDATE electronic_invoices
SET provider_invoice_id = COALESCE(
        NULLIF(provider_response ->> 'id', ''),
        NULLIF(provider_response ->> 'document_id', '')
    )
WHERE provider_invoice_id IS NULL
  AND provider_response IS NOT NULL;

-- ── Proyección de lectura: sales_documents ──────────────────────────────
ALTER TABLE sales_documents
    ADD COLUMN IF NOT EXISTS dian_cufe VARCHAR(200),
    ADD COLUMN IF NOT EXISTS dian_status VARCHAR(30),
    ADD COLUMN IF NOT EXISTS dian_provider_invoice_id VARCHAR(100),
    ADD COLUMN IF NOT EXISTS dian_pdf_url TEXT,
    ADD COLUMN IF NOT EXISTS dian_xml_url TEXT,
    ADD COLUMN IF NOT EXISTS dian_qr_url TEXT,
    ADD COLUMN IF NOT EXISTS dian_synced_at TIMESTAMPTZ;

COMMENT ON COLUMN sales_documents.dian_cufe IS
    'Proyección del CUFE/CUDE canónico de electronic_invoices.';
COMMENT ON COLUMN sales_documents.dian_status IS
    'Proyección del estado DIAN: PENDING_SEND, SENT, ACCEPTED_BY_DIAN, REJECTED_BY_DIAN.';
COMMENT ON COLUMN sales_documents.dian_provider_invoice_id IS
    'Proyección del ID del documento en Siigo.';
COMMENT ON COLUMN sales_documents.dian_pdf_url IS
    'Proyección de la URL del PDF oficial.';
COMMENT ON COLUMN sales_documents.dian_xml_url IS
    'Proyección de la URL del XML oficial.';
COMMENT ON COLUMN sales_documents.dian_qr_url IS
    'Proyección de la URL/representación del QR oficial.';
COMMENT ON COLUMN sales_documents.dian_synced_at IS
    'Última sincronización de estado DIAN en esta fila.';

-- Backfill de la proyección desde la fuente canónica.
UPDATE sales_documents sd
SET dian_cufe = ei.cufe,
    dian_status = ei.status,
    dian_provider_invoice_id = ei.provider_invoice_id,
    dian_pdf_url = ei.pdf_url,
    dian_xml_url = ei.xml_url,
    dian_qr_url = ei.qr_code,
    dian_synced_at = COALESCE(ei.response_at, ei.sent_at, ei.created_at)
FROM electronic_invoices ei
WHERE ei.sales_document_id = sd.id;

CREATE INDEX IF NOT EXISTS idx_sales_documents_dian_status
    ON sales_documents (dian_status)
    WHERE dian_status IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_sales_documents_dian_provider_invoice
    ON sales_documents (dian_provider_invoice_id)
    WHERE dian_provider_invoice_id IS NOT NULL;
