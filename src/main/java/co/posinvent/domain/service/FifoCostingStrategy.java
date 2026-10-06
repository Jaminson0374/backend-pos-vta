package co.posinvent.domain.service;

import co.posinvent.domain.exception.BusinessException;
import co.posinvent.domain.model.CostLayer;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * FIFO costing: an entry creates a new layer at its entry cost; an exit
 * consumes layers in FEFO order (expiry ASC, already ordered by the
 * repository). Pure — no repository, lock, or transaction side effects.
 */
public class FifoCostingStrategy implements CostingStrategy {

    @Override
    public CostingMethod method() {
        return CostingMethod.FIFO;
    }

    @Override
    public EntryCostingResult onEntry(StockEntry entry, List<CostLayer> existing) {
        var layer = new CostLayer(
                null,
                entry.productId(),
                entry.batchId(),
                entry.warehouseId(),
                entry.quantity(),
                entry.unitCost(),
                entry.entryDate(),
                entry.sourceMovementId()
        );
        return new EntryCostingResult(entry.unitCost(), List.of(layer), false);
    }

    @Override
    public CostConsumptionResult onExit(List<CostLayer> layers, BigDecimal quantity) {
        var totalAvailable = BigDecimal.ZERO;
        for (var layer : layers) {
            totalAvailable = totalAvailable.add(layer.remainingQuantity());
        }
        if (totalAvailable.compareTo(quantity) < 0) {
            throw new BusinessException(
                    "INSUFFICIENT_STOCK",
                    "Stock insuficiente para consumir " + quantity
                            + " (disponible: " + totalAvailable + ")");
        }

        var remaining = quantity;
        var totalCost = BigDecimal.ZERO;
        var consumedQty = BigDecimal.ZERO;
        var upserts = new ArrayList<CostLayer>();
        var deletes = new ArrayList<UUID>();

        for (var layer : layers) {
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
                break;
            }

            var fromLayer = layer.remainingQuantity().min(remaining);
            totalCost = totalCost.add(fromLayer.multiply(layer.unitCost()));
            consumedQty = consumedQty.add(fromLayer);
            remaining = remaining.subtract(fromLayer);

            var newRemaining = layer.remainingQuantity().subtract(fromLayer);
            if (newRemaining.compareTo(BigDecimal.ZERO) <= 0) {
                deletes.add(layer.id());
            } else {
                upserts.add(new CostLayer(
                        layer.id(),
                        layer.productId(),
                        layer.batchId(),
                        layer.warehouseId(),
                        newRemaining,
                        layer.unitCost(),
                        layer.entryDate(),
                        layer.sourceMovementId()
                ));
            }
        }

        var consumedUnitCost = consumedQty.compareTo(BigDecimal.ZERO) > 0
                ? totalCost.divide(consumedQty, 6, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        return new CostConsumptionResult(consumedUnitCost, List.copyOf(upserts), List.copyOf(deletes));
    }
}
