-- ============================================================
-- V136: Realineación de cuentas por cobrar desde documentos fuente (A6-1b)
--
-- V134 corregía total_amount = paid_amount + outstanding, pero una AR ya
-- reinflada por un recibo posterior satisface ese invariante y queda fuera.
-- La fuente de verdad son los documentos: el total corregido es el total de
-- la factura más las notas crédito ISSUED (registradas con total negativo).
-- outstanding se deriva de ese total y de lo pagado. Las notas DRAFT no
-- afectan la cartera y no se tocan las AR sin notas ISSUED.
-- ============================================================

WITH corrected AS (
    SELECT ar2.id AS ar_id,
           GREATEST(
               inv.total_amount + COALESCE(SUM(cn.total_amount), 0),
               0
           ) AS corrected_total
    FROM accounts_receivable ar2
    JOIN sales_documents inv ON inv.id = ar2.document_id
    LEFT JOIN sales_documents cn
           ON cn.source_document_id = inv.id
          AND cn.type = 'CREDIT_NOTE'
          AND cn.status = 'ISSUED'
    GROUP BY ar2.id, inv.total_amount
    HAVING COUNT(cn.id) > 0
)
UPDATE accounts_receivable ar
SET total_amount = corrected.corrected_total,
    outstanding = corrected.corrected_total - ar.paid_amount,
    status = CASE
        WHEN corrected.corrected_total - ar.paid_amount <= 0 THEN 'PAID'
        WHEN ar.status = 'OVERDUE' THEN 'OVERDUE'
        WHEN ar.paid_amount = 0 THEN 'OPEN'
        ELSE 'PARTIAL'
    END,
    updated_at = NOW()
FROM corrected
WHERE ar.id = corrected.ar_id
  AND (
      ar.total_amount <> corrected.corrected_total
      OR ar.outstanding <> corrected.corrected_total - ar.paid_amount
  );
