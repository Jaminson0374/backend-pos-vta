package co.posinvent.domain.service;

import co.posinvent.domain.model.CostLayer;

import java.math.BigDecimal;
import java.util.List;

/**
 * Immutable result of costing a stock entry.
 *
 * @param resultingUnitCost the unit cost to report for the entry
 * @param layersToUpsert    layers to create or update
 * @param replaceExisting   when true the orchestrator must first remove all
 *                          existing layers for the product/batch/warehouse
 *                          before persisting {@code layersToUpsert}
 */
public record EntryCostingResult(
        BigDecimal resultingUnitCost,
        List<CostLayer> layersToUpsert,
        boolean replaceExisting
) {}
