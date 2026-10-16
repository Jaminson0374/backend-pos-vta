package co.posinvent.application.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record SupplierInvoiceRequest(
        @NotNull UUID supplierId,
        @NotBlank String invoiceNumber,
        @NotNull LocalDate issueDate,
        LocalDate dueDate,
        @NotNull @DecimalMin("0") BigDecimal subtotal,
        @NotNull @DecimalMin("0") BigDecimal ivaTotal,
        // Optional manual override. When null the retention is computed data-driven from the
        // active PurchaseRetentionConfig catalog (retefuente) plus company_config.purchase_ica_rate.
        @DecimalMin("0") BigDecimal retentionTotal,
        @NotNull @DecimalMin("0") BigDecimal total,
        String notes,
        List<UUID> ocIds
) {}
