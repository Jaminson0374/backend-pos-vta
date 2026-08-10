package co.posinvent.application.dto;

import co.posinvent.domain.model.Batch;
import co.posinvent.domain.model.Batch.BatchStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record BatchResponse(
        UUID id,
        UUID productId,
        UUID supplierId,
        UUID warehouseId,
        LocalDate entryDate,
        BigDecimal initialWeight,
        BigDecimal purchaseCost,
        BatchStatus status,
        String notes,
        UUID createdBy,
        OffsetDateTime createdAt,
        UUID sourceReceiptId,
        UUID ocId,
        String productName,
        String supplierName,
        String warehouseName,
        LocalDate expirationDate,
        String batchType,
        UUID parentBatchId,
        UUID unitOfMeasureId,
        String unitOfMeasureName
) {
    public static BatchResponse from(Batch b) {
        return new BatchResponse(
                b.id(), b.productId(), b.supplierId(), b.warehouseId(),
                b.entryDate(), b.initialWeight(), b.purchaseCost(),
                b.status(), b.notes(), b.createdBy(), b.createdAt(),
                b.sourceReceiptId(), b.ocId(),
                b.productName(), b.supplierName(), b.warehouseName(),
                b.expirationDate(),
                b.batchType() != null ? b.batchType().name() : null,
                b.parentBatchId(),
                b.unitOfMeasureId(),
                null  // unitOfMeasureName — requires LEFT JOIN with units_of_measure table
        );
    }
}
