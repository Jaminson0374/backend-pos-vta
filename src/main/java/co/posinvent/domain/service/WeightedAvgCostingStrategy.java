package co.posinvent.domain.service;

import co.posinvent.domain.model.CostLayer;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Weighted-average costing: an entry merges all existing layers with the
 * incoming quantity into a single averaged layer ({@code Σ(qty×cost)/Σ(qty)}
 * rounded to 6dp HALF_UP); an exit consumes from that single layer. Pure — no
 * repository, lock, or transaction side effects.
 */
public class WeightedAvgCostingStrategy implements CostingStrategy {

    private static final int SCALE = 6;

    @Override
    public CostingMethod method() {
        return CostingMethod.WEIGHTED_AVG;
    }

    @Override
    public EntryCostingResult onEntry(StockEntry entry, List<CostLayer> existing) {
        var totalQty = entry.quantity();
        var totalValue = entry.quantity().multiply(entry.unitCost());

        for (var layer : existing) {
            totalQty = totalQty.add(layer.remainingQuantity());
            totalValue = totalValue.add(layer.remainingQuantity().multiply(layer.unitCost()));
        }

        var avgCost = totalQty.compareTo(BigDecimal.ZERO) > 0
                ? totalValue.divide(totalQty, SCALE, RoundingMode.HALF_UP)
                : entry.unitCost();

        var merged = new CostLayer(
                null,
                entry.productId(),
                entry.batchId(),
                entry.warehouseId(),
                totalQty,
                avgCost,
                entry.entryDate(),
                null
        );

        return new EntryCostingResult(avgCost, List.of(merged), true);
    }

    @Override
    public CostConsumptionResult onExit(List<CostLayer> layers, BigDecimal quantity) {
        if (layers.isEmpty()) {
            return new CostConsumptionResult(BigDecimal.ZERO, List.of(), List.of());
        }

        var layer = layers.get(0);
        var consumedUnitCost = layer.unitCost();

        var newQty = layer.remainingQuantity().subtract(quantity);
        if (newQty.compareTo(BigDecimal.ZERO) <= 0) {
            return new CostConsumptionResult(consumedUnitCost, List.of(), List.of(layer.id()));
        }

        var updated = new CostLayer(
                layer.id(),
                layer.productId(),
                layer.batchId(),
                layer.warehouseId(),
                newQty,
                layer.unitCost(),
                layer.entryDate(),
                layer.sourceMovementId()
        );
        return new CostConsumptionResult(consumedUnitCost, List.of(updated), List.of());
    }
}
