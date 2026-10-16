package co.posinvent.domain.service;

import co.posinvent.domain.exception.BusinessException;
import co.posinvent.domain.model.InvoiceStatus;
import co.posinvent.domain.model.Payment.InvoicePayment;
import co.posinvent.domain.model.SupplierInvoice;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Validación pura de dominio para pagos.
 * No tiene dependencias de infraestructura ni Spring.
 */
public final class PaymentDomainService {

    /**
     * Validates that the payment amount is greater than zero.
     *
     * @throws BusinessException with code INVALID_PAYMENT_AMOUNT
     */
    public void validateAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(
                    "INVALID_PAYMENT_AMOUNT",
                    "El monto del pago debe ser mayor a cero"
            );
        }
    }

    /**
     * Validates that all invoices are payable and the applied amounts don't exceed the remaining
     * balance of each invoice (A1).
     *
     * <p>The remaining balance comes from {@code pendingByInvoice} — normally the CxP outstanding,
     * i.e. {@code total} minus every previous {@code invoice_payments} entry. When a mapping is
     * absent for an invoice the raw {@code total} is used as a fallback (legacy/standalone data).</p>
     *
     * @param invoices          the invoices being paid
     * @param invoicePayments   how the payment is split across invoices
     * @param totalAmount       the total payment amount
     * @param pendingByInvoice  remaining balance per invoice id (may be null/empty)
     * @throws BusinessException with code INVOICE_NOT_PAYABLE or OVERPAYMENT
     */
    public void validateInvoicePayments(
            List<SupplierInvoice> invoices,
            List<InvoicePayment> invoicePayments,
            BigDecimal totalAmount,
            Map<UUID, BigDecimal> pendingByInvoice
    ) {
        // Each invoice must be in RECONCILED status
        for (var invoice : invoices) {
            if (invoice.status() != InvoiceStatus.RECONCILED) {
                throw new BusinessException(
                        "INVOICE_NOT_PAYABLE",
                        "La factura " + invoice.invoiceNumber() +
                        " no está en estado RECONCILED. Estado: " + invoice.status()
                );
            }
        }

        // Sum of applied amounts must equal total payment amount (within 0.01 tolerance)
        var appliedSum = invoicePayments.stream()
                .map(InvoicePayment::appliedAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        var diff = totalAmount.subtract(appliedSum).abs();
        if (diff.compareTo(new BigDecimal("0.01")) > 0) {
            throw new BusinessException(
                    "INVALID_PAYMENT_AMOUNT",
                    "La suma de montos aplicados (" + appliedSum +
                    ") no coincide con el total del pago (" + totalAmount + ")"
            );
        }

        // Merge duplicate invoice lines instead of crashing Collectors.toMap (A1). A duplicated
        // invoice id is aggregated so the per-invoice balance check sees the real applied amount.
        var invoiceById = invoices.stream()
                .collect(Collectors.toMap(SupplierInvoice::id, inv -> inv, (a, b) -> a));

        var appliedByInvoice = invoicePayments.stream()
                .collect(Collectors.toMap(
                        InvoicePayment::invoiceId,
                        InvoicePayment::appliedAmount,
                        BigDecimal::add));

        for (var entry : appliedByInvoice.entrySet()) {
            var inv = invoiceById.get(entry.getKey());
            if (inv == null) continue;

            var applied = entry.getValue();

            // Amount being applied must be > 0
            if (applied.compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException(
                        "INVALID_PAYMENT_AMOUNT",
                        "El monto aplicado a la factura " + inv.invoiceNumber() +
                        " debe ser mayor a cero"
                );
            }

            var pending = pendingByInvoice != null
                    ? pendingByInvoice.getOrDefault(inv.id(), inv.total())
                    : inv.total();

            // Cannot pay more than the remaining balance (not the raw invoice total)
            if (applied.compareTo(pending) > 0) {
                throw new BusinessException(
                        "OVERPAYMENT",
                        "El monto aplicado (" + applied +
                        ") excede el saldo pendiente de la factura " + inv.invoiceNumber() +
                        " (" + pending + ")"
                );
            }
        }
    }
}
