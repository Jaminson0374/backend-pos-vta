package co.posinvent.domain.model;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record PurchaseOrder(
        UUID id,
        UUID supplierId,
        PurchaseOrderStatus status,
        LocalDate orderDate,
        String documentNumber,
        String notes,
        LocalDate dueDate,
        UUID buyerId,
        String paymentMethod,
        String supportDocumentType,
        String supportDocumentNumber,
        String currency,
        UUID createdBy,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        Long version,
        List<PurchaseLineItem> lines
) {
    public PurchaseOrder {
        if (lines == null) lines = List.of();
        if (currency == null || currency.isBlank()) currency = "COP";
    }

    public boolean isMutable() {
        return status == PurchaseOrderStatus.PENDING;
    }
}
