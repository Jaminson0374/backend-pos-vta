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
        String reason
) {
    public SalesDocument {
        if (items == null) items = List.of();
        if (isCreditSale == null) isCreditSale = false;
    }
}
