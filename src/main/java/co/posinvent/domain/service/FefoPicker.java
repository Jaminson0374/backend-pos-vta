package co.posinvent.domain.service;

import co.posinvent.domain.exception.BusinessException;
import co.posinvent.domain.model.BatchAllocation;
import co.posinvent.domain.model.InventoryStock;
import co.posinvent.domain.repository.StockRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class FefoPicker {

    private final StockRepository stockRepository;

    public FefoPicker(StockRepository stockRepository) {
        this.stockRepository = stockRepository;
    }

    /**
     * Selects batches to pick from using FEFO (First Expired, First Out) algorithm.
     * Batches are already ordered by expiration_date ASC NULLS LAST, entry_date ASC
     * by the repository query.
     *
     * @param productId   the product to pick
     * @param warehouseId the warehouse to pick from
     * @param requiredQty the total quantity needed
     * @return list of batch allocations with quantities and unit costs
     * @throws BusinessException if no stock available or insufficient stock
     */
    public List<BatchAllocation> pick(UUID productId, UUID warehouseId, BigDecimal requiredQty) {
        List<InventoryStock> stockEntries =
                stockRepository.findAvailableByProductWarehouse(productId, warehouseId);

        if (stockEntries.isEmpty()) {
            throw new BusinessException(
                    "NO_STOCK_AVAILABLE",
                    "No hay stock disponible para el producto " + productId
                            + " en la bodega " + warehouseId);
        }

        var remaining = requiredQty;
        var allocations = new ArrayList<BatchAllocation>();

        for (InventoryStock stock : stockEntries) {
            BigDecimal available = stock.availableQuantity();

            if (available.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            BigDecimal takenQty = remaining.min(available);

            allocations.add(new BatchAllocation(
                    stock.batchId(),
                    takenQty,
                    stock.unitCost()));

            remaining = remaining.subtract(takenQty);

            if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
                break;
            }
        }

        if (remaining.compareTo(BigDecimal.ZERO) > 0) {
            throw new BusinessException(
                    "INSUFFICIENT_STOCK",
                    "Stock insuficiente para el producto " + productId
                            + " en la bodega " + warehouseId
                            + ". Requerido: " + requiredQty
                            + ", disponible: " + requiredQty.subtract(remaining));
        }

        return allocations;
    }
}
