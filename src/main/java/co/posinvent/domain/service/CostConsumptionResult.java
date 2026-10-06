package co.posinvent.domain.service;

import co.posinvent.domain.model.CostLayer;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Immutable result of costing a stock exit.
 *
 * @param consumedUnitCost  the weighted-average unit cost of consumed quantity
 * @param layersToUpsert    partially consumed layers with their new remaining
 *                          quantity
 * @param layerIdsToDelete  ids of fully consumed layers to remove
 */
public record CostConsumptionResult(
        BigDecimal consumedUnitCost,
        List<CostLayer> layersToUpsert,
        List<UUID> layerIdsToDelete
) {}
