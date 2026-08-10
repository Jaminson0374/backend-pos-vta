package co.posinvent.application.dto;

import co.posinvent.domain.model.Batch.BatchStatus;
import co.posinvent.domain.model.ManualDespostePlan;
import co.posinvent.domain.model.ManualDespostePlan.SourceBatchAction;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record ManualDesposteResponse(
        UUID sourceBatchId,
        DesposteMassBalanceResponse massBalance,
        BigDecimal totalCommercialValue,
        BigDecimal totalAllocatedCost,
        List<ManualDesposteCutResultResponse> cuts,
        List<StockUpsertDraftResponse> stockUpserts,
        SourceBatchTransitionResponse sourceBatchTransition,
        List<UUID> childBatchIds
) {
    public static ManualDesposteResponse from(ManualDespostePlan plan) {
        return new ManualDesposteResponse(
                plan.sourceBatchId(),
                DesposteMassBalanceResponse.from(plan.massBalance()),
                plan.totalCommercialValue(),
                plan.totalAllocatedCost(),
                plan.cuts().stream().map(ManualDesposteCutResultResponse::from).toList(),
                plan.stockUpserts().stream().map(StockUpsertDraftResponse::from).toList(),
                SourceBatchTransitionResponse.from(plan.sourceBatchTransition()),
                List.of() // populated by the use case after child batches are created
        );
    }

    /**
     * Factory for building a response with childBatchIds populated from created batches.
     */
    public static ManualDesposteResponse withChildBatches(
            ManualDespostePlan plan,
            List<UUID> childBatchIds
    ) {
        return new ManualDesposteResponse(
                plan.sourceBatchId(),
                DesposteMassBalanceResponse.from(plan.massBalance()),
                plan.totalCommercialValue(),
                plan.totalAllocatedCost(),
                plan.cuts().stream().map(ManualDesposteCutResultResponse::from).toList(),
                plan.stockUpserts().stream().map(StockUpsertDraftResponse::from).toList(),
                SourceBatchTransitionResponse.from(plan.sourceBatchTransition()),
                List.copyOf(childBatchIds)
        );
    }

    public record DesposteMassBalanceResponse(
            BigDecimal inputWeight,
            BigDecimal totalCutsWeight,
            BigDecimal wasteWeight,
            BigDecimal shrinkWeight,
            BigDecimal deviation,
            BigDecimal tolerance,
            boolean withinTolerance
    ) {
        static DesposteMassBalanceResponse from(ManualDespostePlan.DesposteMassBalance massBalance) {
            return new DesposteMassBalanceResponse(
                    massBalance.inputWeight(),
                    massBalance.totalCutsWeight(),
                    massBalance.wasteWeight(),
                    massBalance.shrinkWeight(),
                    massBalance.deviation(),
                    massBalance.tolerance(),
                    massBalance.withinTolerance()
            );
        }
    }

    public record ManualDesposteCutResultResponse(
            UUID productId,
            UUID warehouseId,
            BigDecimal weight,
            BigDecimal suggestedSalePrice,
            BigDecimal commercialValue,
            BigDecimal allocatedCost,
            BigDecimal unitCost,
            LocalDate expirationDate
    ) {
        static ManualDesposteCutResultResponse from(ManualDespostePlan.ManualDesposteCutResult cut) {
            return new ManualDesposteCutResultResponse(
                    cut.productId(),
                    cut.warehouseId(),
                    cut.weight(),
                    cut.suggestedSalePrice(),
                    cut.commercialValue(),
                    cut.allocatedCost(),
                    cut.unitCost(),
                    cut.expirationDate()
            );
        }
    }

    public record StockUpsertDraftResponse(
            UUID productId,
            UUID batchId,
            UUID warehouseId,
            BigDecimal quantityDelta,
            BigDecimal unitCost
    ) {
        static StockUpsertDraftResponse from(ManualDespostePlan.StockUpsertDraft stockUpsert) {
            return new StockUpsertDraftResponse(
                    stockUpsert.productId(),
                    stockUpsert.batchId(),
                    stockUpsert.warehouseId(),
                    stockUpsert.quantityDelta(),
                    stockUpsert.unitCost()
            );
        }
    }

    public record SourceBatchTransitionResponse(
            UUID batchId,
            BatchStatus previousStatus,
            BatchStatus nextStatus,
            SourceBatchAction action
    ) {
        static SourceBatchTransitionResponse from(ManualDespostePlan.SourceBatchTransition transition) {
            return new SourceBatchTransitionResponse(
                    transition.batchId(),
                    transition.previousStatus(),
                    transition.nextStatus(),
                    transition.action()
            );
        }
    }
}
