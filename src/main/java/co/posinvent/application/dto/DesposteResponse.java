package co.posinvent.application.dto;

import co.posinvent.domain.model.Desposte;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record DesposteResponse(
        UUID id,
        UUID sourceBatchId,
        UUID productId,
        UUID warehouseId,
        BigDecimal inputWeight,
        BigDecimal totalCutsWeight,
        BigDecimal wasteWeight,
        BigDecimal shrinkWeight,
        BigDecimal deviation,
        BigDecimal tolerance,
        boolean withinTolerance,
        BigDecimal yieldPercentage,
        BigDecimal totalCommercialValue,
        BigDecimal totalAllocatedCost,
        String notes,
        String createdBy,
        OffsetDateTime createdAt,
        List<DesposteCutResponse> cuts
) {
    public static DesposteResponse from(Desposte desposte) {
        return new DesposteResponse(
                desposte.id(),
                desposte.sourceBatchId(),
                desposte.productId(),
                desposte.warehouseId(),
                desposte.inputWeight(),
                desposte.totalCutsWeight(),
                desposte.wasteWeight(),
                desposte.shrinkWeight(),
                desposte.deviation(),
                desposte.tolerance(),
                desposte.withinTolerance(),
                desposte.yieldPercentage(),
                desposte.totalCommercialValue(),
                desposte.totalAllocatedCost(),
                desposte.notes(),
                desposte.createdBy(),
                desposte.createdAt(),
                desposte.cuts().stream().map(DesposteCutResponse::from).toList()
        );
    }

    public record DesposteCutResponse(
            UUID id,
            UUID productId,
            UUID warehouseId,
            UUID childBatchId,
            BigDecimal weight,
            BigDecimal suggestedSalePrice,
            BigDecimal commercialValue,
            BigDecimal allocatedCost,
            BigDecimal unitCost,
            LocalDate expirationDate
    ) {
        static DesposteCutResponse from(Desposte.DesposteCut cut) {
            return new DesposteCutResponse(
                    cut.id(),
                    cut.productId(),
                    cut.warehouseId(),
                    cut.childBatchId(),
                    cut.weight(),
                    cut.suggestedSalePrice(),
                    cut.commercialValue(),
                    cut.allocatedCost(),
                    cut.unitCost(),
                    cut.expirationDate()
            );
        }
    }
}
