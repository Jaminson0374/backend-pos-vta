package co.posinvent.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Persisted record of an executed manual desposte (MVM).
 *
 * <p>The header mirrors the mass balance and totals produced by
 * {@code ManualDesposteDomainService}; each {@link DesposteCut} is a resulting
 * cut linked to the child batch minted for it.</p>
 */
public record Desposte(
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
        List<DesposteCut> cuts
) {
    public Desposte {
        if (cuts == null) {
            cuts = List.of();
        }
        cuts = List.copyOf(cuts);
    }

    public record DesposteCut(
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
    ) { }
}
