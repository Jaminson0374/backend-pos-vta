package co.posinvent.application.dto;

import co.posinvent.domain.model.InvoiceStatus;
import co.posinvent.domain.model.SupplierInvoice;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record SupplierInvoiceResponse(
        UUID id,
        UUID supplierId,
        String supplierName,
        String invoiceNumber,
        LocalDate issueDate,
        LocalDate dueDate,
        BigDecimal subtotal,
        BigDecimal ivaTotal,
        BigDecimal retentionTotal,
        BigDecimal total,
        InvoiceStatus status,
        String notes,
        UUID createdBy,
        OffsetDateTime createdAt,
        List<UUID> ocIds
) {
    public static SupplierInvoiceResponse from(SupplierInvoice invoice) {
        return from(invoice, null);
    }

    public static SupplierInvoiceResponse from(SupplierInvoice invoice, String supplierName) {
        return new SupplierInvoiceResponse(
                invoice.id(),
                invoice.supplierId(),
                supplierName,
                invoice.invoiceNumber(),
                invoice.issueDate(),
                invoice.dueDate(),
                invoice.subtotal(),
                invoice.ivaTotal(),
                invoice.retentionTotal(),
                invoice.total(),
                invoice.status(),
                invoice.notes(),
                invoice.createdBy(),
                invoice.createdAt(),
                invoice.ocIds()
        );
    }
}
