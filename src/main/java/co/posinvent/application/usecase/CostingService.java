package co.posinvent.application.usecase;

import co.posinvent.domain.model.CostLayer;
import co.posinvent.domain.repository.CostLayerRepository;
import co.posinvent.domain.repository.ProductRepository;
import co.posinvent.domain.repository.StockRepository;
import co.posinvent.infrastructure.adapters.out.persistence.StockJpaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class CostingService {

    private final CostLayerRepository layerRepo;
    private final ProductRepository productRepo;
    private final StockRepository stockRepo;

    // Direct JPA dependency for the targeted PESSIMISTIC_WRITE lock on the
    // concrete inventory_stock row (delete+re-insert has no row to version-check).
    // Field-injected so the existing 3-arg constructor used by unit tests stays
    // intact; when null (plain unit tests) the lock is skipped.
    @Autowired
    private StockJpaRepository stockJpaRepository;

    public CostingService(CostLayerRepository layerRepo, ProductRepository productRepo, StockRepository stockRepo) {
        this.layerRepo = layerRepo;
        this.productRepo = productRepo;
        this.stockRepo = stockRepo;
    }

    /**
     * Called when stock enters inventory. Creates a cost layer for PEPS,
     * or recalculates weighted average for PROMEDIO_PONDERADO.
     */
    @Transactional
    public BigDecimal resolveCostOnEntry(
            UUID productId, UUID batchId, UUID warehouseId,
            BigDecimal quantity, BigDecimal unitCost,
            UUID sourceMovementId
    ) {
        var product = productRepo.findById(productId).orElse(null);
        if (product == null) return unitCost;

        String method = product.costingMethod();

        if ("PEPS".equals(method)) {
            // Simply create a new layer for FIFO consumption later
            layerRepo.save(new CostLayer(
                    null, productId, batchId, warehouseId,
                    quantity, unitCost, OffsetDateTime.now(), sourceMovementId
            ));
            return unitCost;
        }

        if ("PROMEDIO_PONDERADO".equals(method)) {
            // Serialize concurrent weighted-average recalculations by locking the
            // concrete stock row. The delete-all + re-insert below has no row to
            // version-check, so @Version alone can't prevent duplicate/orphan layers.
            if (stockJpaRepository != null) {
                stockJpaRepository.lockForUpdate(productId, batchId, warehouseId);
            }

            // Recalculate weighted average
            var layers = layerRepo.findByProductBatchWarehouse(productId, batchId, warehouseId);
            var totalQty = quantity;
            var totalValue = quantity.multiply(unitCost);

            for (var layer : layers) {
                totalQty = totalQty.add(layer.remainingQuantity());
                totalValue = totalValue.add(layer.remainingQuantity().multiply(layer.unitCost()));
            }

            var avgCost = totalQty.compareTo(BigDecimal.ZERO) > 0
                    ? totalValue.divide(totalQty, 6, RoundingMode.HALF_UP)
                    : unitCost;

            // Replace all old layers with one averaged layer
            layerRepo.deleteAllByProductBatchWarehouse(productId, batchId, warehouseId);
            layerRepo.save(new CostLayer(
                    null, productId, batchId, warehouseId,
                    totalQty, avgCost, OffsetDateTime.now(), null
            ));

            return avgCost;
        }

        // Default: just create a layer
        layerRepo.save(new CostLayer(
                null, productId, batchId, warehouseId,
                quantity, unitCost, OffsetDateTime.now(), sourceMovementId
        ));
        return unitCost;
    }

    /**
     * Called when stock exits. Consumes layers according to costing method.
     * Returns the average unit cost of consumed quantities.
     */
    @Transactional
    public BigDecimal resolveCostOnExit(
            UUID productId, UUID batchId, UUID warehouseId, BigDecimal quantity
    ) {
        var product = productRepo.findById(productId).orElse(null);
        if (product == null) return BigDecimal.ZERO;

        String method = product.costingMethod();

        if ("PEPS".equals(method)) {
            // FEFO: consume layers ordered by batch expirationDate ASC (earliest expires first)
            var layers = new ArrayList<>(layerRepo.findByProductBatchWarehouseFefo(productId, batchId, warehouseId));
            if (layers.isEmpty()) return BigDecimal.ZERO;
            return consumeFifo(layers, quantity);
        }

        // PROMEDIO_PONDERADO or default: consume from the single averaged layer
        var layers = new ArrayList<>(layerRepo.findByProductBatchWarehouse(productId, batchId, warehouseId));
        if (layers.isEmpty()) return BigDecimal.ZERO;

        var layer = layers.get(0);
        var consumedCost = layer.unitCost();

        var newQty = layer.remainingQuantity().subtract(quantity);
        if (newQty.compareTo(BigDecimal.ZERO) <= 0) {
            layerRepo.deleteAllByProductBatchWarehouse(productId, batchId, warehouseId);
        } else {
            layerRepo.save(new CostLayer(
                    layer.id(), productId, batchId, warehouseId,
                    newQty, layer.unitCost(), layer.entryDate(), layer.sourceMovementId()
            ));
        }

        return consumedCost;
    }

    private BigDecimal consumeFifo(List<CostLayer> layers, BigDecimal quantity) {
        var remaining = quantity;
        var totalCost = BigDecimal.ZERO;
        var consumedQty = BigDecimal.ZERO;

        for (var layer : layers) {
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) break;

            var fromLayer = layer.remainingQuantity().min(remaining);
            totalCost = totalCost.add(fromLayer.multiply(layer.unitCost()));
            consumedQty = consumedQty.add(fromLayer);
            remaining = remaining.subtract(fromLayer);

            var newRemaining = layer.remainingQuantity().subtract(fromLayer);
            if (newRemaining.compareTo(BigDecimal.ZERO) <= 0) {
                // Layer fully consumed — delete it (handled by cascade or explicit)
                layerRepo.save(new CostLayer(
                        layer.id(), layer.productId(), layer.batchId(), layer.warehouseId(),
                        BigDecimal.ZERO, layer.unitCost(), layer.entryDate(), layer.sourceMovementId()
                ));
            } else {
                layerRepo.save(new CostLayer(
                        layer.id(), layer.productId(), layer.batchId(), layer.warehouseId(),
                        newRemaining, layer.unitCost(), layer.entryDate(), layer.sourceMovementId()
                ));
            }
        }

        return consumedQty.compareTo(BigDecimal.ZERO) > 0
                ? totalCost.divide(consumedQty, 6, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
    }

    @Transactional
    public void recalculateUnitCost(UUID productId, UUID batchId, UUID warehouseId) {
        var layers = layerRepo.findByProductBatchWarehouse(productId, batchId, warehouseId);
        if (layers.isEmpty()) return;

        var totalQty = BigDecimal.ZERO;
        var totalValue = BigDecimal.ZERO;
        for (var layer : layers) {
            totalQty = totalQty.add(layer.remainingQuantity());
            totalValue = totalValue.add(layer.remainingQuantity().multiply(layer.unitCost()));
        }

        var avgCost = totalQty.compareTo(BigDecimal.ZERO) > 0
                ? totalValue.divide(totalQty, 6, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        var stock = stockRepo.findByProductBatchWarehouse(productId, batchId, warehouseId);
        if (stock.isPresent()) {
            var s = stock.get();
            stockRepo.save(new co.posinvent.domain.model.InventoryStock(
                    s.id(), s.productId(), s.batchId(), s.warehouseId(),
                    s.currentQuantity(), s.committedQuantity(), avgCost,
                    s.createdAt(), s.updatedAt()
            ));
        }
    }
}
