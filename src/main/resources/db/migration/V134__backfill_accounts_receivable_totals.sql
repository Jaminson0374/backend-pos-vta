-- ============================================================
-- V134: Backfill histórico de totales de cuentas por cobrar (A6-1)
--
-- Antes de A4, una devolución de venta a crédito reducía outstanding
-- sin reducir total_amount. Un recibo posterior recalcula
-- outstanding = total_amount - paid_amount y volvía a inflar la deuda.
--
-- total_amount := paid_amount + outstanding conserva los montos pagado y
-- pendiente y restaura el invariante. Solo se tocan AR con nota crédito
-- asociada y total desalineado. OVERDUE se conserva mientras haya saldo.
-- ============================================================

UPDATE accounts_receivable ar
SET total_amount = ar.paid_amount + ar.outstanding,
    status = CASE
        WHEN ar.outstanding <= 0 THEN 'PAID'
        WHEN ar.status = 'OVERDUE' THEN 'OVERDUE'
        WHEN ar.paid_amount = 0 THEN 'OPEN'
        ELSE 'PARTIAL'
    END,
    updated_at = NOW()
WHERE ar.total_amount <> ar.paid_amount + ar.outstanding
  AND EXISTS (
      SELECT 1
      FROM sales_documents cn
      WHERE cn.type = 'CREDIT_NOTE'
        AND cn.source_document_id = ar.document_id
  );
