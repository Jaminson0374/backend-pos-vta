package co.posinvent.domain.model;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ProductionBatch(
        UUID id,
        UUID formulaId,
        BigDecimal quantityProduced,
        BigDecimal expectedQuantity,
        BigDecimal directMaterialCost,
        BigDecimal directLaborCost,
        BigDecimal overheadCost,
        BigDecimal totalCost,
        BigDecimal unitCost,
        BigDecimal shrinkageQuantity,
        BigDecimal shrinkageCost,
        String notes,
        UUID createdBy,
        OffsetDateTime createdAt,
        UUID batchId
) {}
