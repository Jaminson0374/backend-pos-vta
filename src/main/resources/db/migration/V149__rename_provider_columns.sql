-- V149: rename provider-specific tax/sync columns without data loss.
ALTER TABLE taxes RENAME COLUMN siigo_tax_id TO provider_external_id;
ALTER TABLE sales_documents RENAME COLUMN dian_synced_at TO provider_synced_at;

COMMENT ON COLUMN taxes.provider_external_id IS 'ID externo del impuesto en el proveedor tecnológico.';
COMMENT ON COLUMN sales_documents.provider_synced_at IS 'Última sincronización con el proveedor tecnológico.';
