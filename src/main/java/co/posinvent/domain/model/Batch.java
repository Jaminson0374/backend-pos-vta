package co.posinvent.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record Batch(
        UUID id,
        UUID productId,
        UUID supplierId,
        UUID warehouseId,
        LocalDate entryDate,
        BigDecimal initialWeight,
        BigDecimal purchaseCost,
        BatchStatus status,
        String notes,
        LocalDate expirationDate,
        UUID createdBy,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        UUID updatedBy,
        UUID sourceReceiptId,
        UUID ocId,
        // Enriched display fields — populated by LEFT JOIN queries, null otherwise
        String productName,
        String supplierName,
        String warehouseName,
        UUID parentBatchId,
        BatchType batchType,
        UUID unitOfMeasureId
) {
    public enum BatchStatus { OPEN, PROCESSING, CLOSED }

    public boolean isMutable() {
        return status == BatchStatus.OPEN;
    }
}
