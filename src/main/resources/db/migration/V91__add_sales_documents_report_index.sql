CREATE INDEX IF NOT EXISTS idx_sales_documents_report
    ON sales_documents(type, status, created_at, warehouse_id);
