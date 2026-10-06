package co.posinvent.domain.service;

import co.posinvent.domain.model.CostLayer;

import java.math.BigDecimal;
import java.util.List;

/**
 * Pure costing algorithm contract. Implementations compute what to persist
 * (immutable value objects) and MUST NOT perform repository I/O, acquire
 * locks, or open transactions.
 */
public interface CostingStrategy {

    CostingMethod method();

    /**
     * Cost a stock entry: create (FIFO) or merge (weighted-average) a layer.
     *
     * @param entry    the incoming stock movement
     * @param existing the current cost layers for the product/batch/warehouse
     * @return the unit cost to report plus the layers to persist
     */
    EntryCostingResult onEntry(StockEntry entry, List<CostLayer> existing);

    /**
     * Cost a stock exit: consume layers according to the strategy.
     *
     * @param layers   the current layers, ordered by expiry ASC for FEFO
     * @param quantity the quantity to consume
     * @return the consumed unit cost plus the layer mutations
     */
    CostConsumptionResult onExit(List<CostLayer> layers, BigDecimal quantity);
}
