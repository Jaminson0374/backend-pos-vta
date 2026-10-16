package co.posinvent.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record SalesDocument(
        UUID id,
        SalesDocumentType type,
        SalesDocumentStatus status,
        String documentNumber,
        UUID clientId,
        UUID warehouseId,
        UUID shiftId,
        UUID cashRegisterId,
        UUID sourceDocumentId,
        BigDecimal totalNet,
        BigDecimal totalTax0,
        BigDecimal totalTax5,
        BigDecimal totalTax19,
        BigDecimal totalAmount,
        UUID createdBy,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        List<SaleItem> items,
        LocalDate dueDate,
        Boolean isCreditSale,
        String reason,
        BigDecimal discountTotal,
        BigDecimal documentDiscount,
        String documentDiscountType,
        BigDecimal documentDiscountValue,
        UUID discountAuthorizedBy,
        String discountReason
) {
    public static final String DISCOUNT_NONE = "NONE";

    public SalesDocument {
        if (items == null) items = List.of();
        if (isCreditSale == null) isCreditSale = false;
        if (discountTotal == null) discountTotal = BigDecimal.ZERO;
        if (documentDiscount == null) documentDiscount = BigDecimal.ZERO;
        if (documentDiscountType == null || documentDiscountType.isBlank()) documentDiscountType = DISCOUNT_NONE;
        if (documentDiscountValue == null) documentDiscountValue = BigDecimal.ZERO;
    }

    /** Backwards-compatible constructor for documents without discounts. */
    public SalesDocument(
            UUID id,
            SalesDocumentType type,
            SalesDocumentStatus status,
            String documentNumber,
            UUID clientId,
            UUID warehouseId,
            UUID shiftId,
            UUID cashRegisterId,
            UUID sourceDocumentId,
            BigDecimal totalNet,
            BigDecimal totalTax0,
            BigDecimal totalTax5,
            BigDecimal totalTax19,
            BigDecimal totalAmount,
            UUID createdBy,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt,
            List<SaleItem> items,
            LocalDate dueDate,
            Boolean isCreditSale,
            String reason
    ) {
        this(id, type, status, documentNumber, clientId, warehouseId, shiftId, cashRegisterId,
                sourceDocumentId, totalNet, totalTax0, totalTax5, totalTax19, totalAmount,
                createdBy, createdAt, updatedAt, items, dueDate, isCreditSale, reason,
                BigDecimal.ZERO, BigDecimal.ZERO, DISCOUNT_NONE, BigDecimal.ZERO, null, null);
    }
}
