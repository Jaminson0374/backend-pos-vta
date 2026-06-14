-- ============================================================ 
-- V82: Add DEBIT_NOTE to sales_documents.type CHECK constraint
-- ============================================================

ALTER TABLE sales_documents DROP CONSTRAINT IF EXISTS sales_documents_type_check;

ALTER TABLE sales_documents ADD CONSTRAINT sales_documents_type_check
    CHECK (type IN ('QUOTE','ORDER','INVOICE','CREDIT_NOTE','DEBIT_NOTE'));
