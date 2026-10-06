package co.posinvent.application.usecase;

import co.posinvent.domain.model.CompanyConfig;
import co.posinvent.domain.model.CostLayer;
import co.posinvent.domain.model.InventoryStock;
import co.posinvent.domain.repository.CompanyConfigRepository;
import co.posinvent.domain.repository.CostLayerRepository;
import co.posinvent.domain.repository.ProductRepository;
import co.posinvent.domain.repository.StockRepository;
import co.posinvent.domain.service.CostingMethod;
import co.posinvent.domain.service.CostingStrategy;
import co.posinvent.domain.service.FifoCostingStrategy;
import co.posinvent.domain.service.StockEntry;
import co.posinvent.domain.service.WeightedAvgCostingStrategy;
import co.posinvent.infrastructure.adapters.out.persistence.StockJpaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Hexagonal orchestrator for stock costing. Resolves the GLOBAL costing method
 * from {@code company_config.costing_method}, selects the matching pure
 * {@link CostingStrategy}, loads layers via the repository, delegates the pure
 * calculation, and persists the returned value objects. Repository I/O, the C1
 * {@code PESSIMISTIC_WRITE} lock, and {@code @Transactional} remain here in the
 * application layer — never in the domain strategies.
 */
@Service
public class CostingOrchestrator {

    private static final Map<CostingMethod, CostingStrategy> STRATEGIES = Map.of(
            CostingMethod.FIFO, new FifoCostingStrategy(),
            CostingMethod.WEIGHTED_AVG, new WeightedAvgCostingStrategy()
    );

    private final CostLayerRepository layerRepo;
    private final ProductRepository productRepo;
    private final StockRepository stockRepo;
    private final CompanyConfigRepository configRepo;

    // Direct JPA dependency for the targeted PESSIMISTIC_WRITE lock on the
    // concrete inventory_stock row (delete+re-insert has no row to version-check).
    // Field-injected so the constructor used by unit tests stays intact; when
    // null (plain unit tests) the lock is skipped.
    @Autowired
    private StockJpaRepository stockJpaRepository;

    public CostingOrchestrator(
            CostLayerRepository layerRepo,
            ProductRepository productRepo,
            StockRepository stockRepo,
            CompanyConfigRepository configRepo) {
        this.layerRepo = layerRepo;
        this.productRepo = productRepo;
        this.stockRepo = stockRepo;
        this.configRepo = configRepo;
    }

    /**
     * Called when stock enters inventory. Resolves the global method, selects
     * the strategy, loads existing layers, and delegates the pure entry calc.
     */
    @Transactional
    public BigDecimal resolveCostOnEntry(
            UUID productId, UUID batchId, UUID warehouseId,
            BigDecimal quantity, BigDecimal unitCost,
            UUID sourceMovementId
    ) {
        if (productRepo.findById(productId).isEmpty()) return unitCost;

        var method = resolveCostingMethod();
        var strategy = strategyFor(method);

        // C1 lock: serialize weighted-average recalculations. The delete-all +
        // re-insert has no row to version-check, so @Version alone can't prevent
        // duplicate/orphan layers. Acquired BEFORE reading layers and BEFORE
        // persisting (unchanged lock order).
        if (method == CostingMethod.WEIGHTED_AVG && stockJpaRepository != null) {
            stockJpaRepository.lockForUpdate(productId, batchId, warehouseId);
        }

        var entry = new StockEntry(
                productId, batchId, warehouseId, quantity, unitCost,
                sourceMovementId, OffsetDateTime.now()
        );
        var existing = method == CostingMethod.WEIGHTED_AVG
                ? layerRepo.findByProductBatchWarehouse(productId, batchId, warehouseId)
                : List.<CostLayer>of();

        var result = strategy.onEntry(entry, existing);

        if (result.replaceExisting()) {
            layerRepo.deleteAllByProductBatchWarehouse(productId, batchId, warehouseId);
        }
        for (var layer : result.layersToUpsert()) {
            layerRepo.save(layer);
        }

        return result.resultingUnitCost();
    }

    /**
     * Called when stock exits. Resolves the global method, loads layers in
     * strategy order (FEFO for FIFO), delegates the pure exit calc, and
     * persists the returned mutations.
     */
    @Transactional
    public BigDecimal resolveCostOnExit(
            UUID productId, UUID batchId, UUID warehouseId, BigDecimal quantity
    ) {
        if (productRepo.findById(productId).isEmpty()) return BigDecimal.ZERO;

        var method = resolveCostingMethod();
        var strategy = strategyFor(method);

        var layers = method == CostingMethod.FIFO
                ? new ArrayList<>(layerRepo.findByProductBatchWarehouseFefo(productId, batchId, warehouseId))
                : new ArrayList<>(layerRepo.findByProductBatchWarehouse(productId, batchId, warehouseId));

        if (layers.isEmpty()) return BigDecimal.ZERO;

        var result = strategy.onExit(layers, quantity);

        for (var id : result.layerIdsToDelete()) {
            layerRepo.deleteById(id);
        }
        for (var layer : result.layersToUpsert()) {
            layerRepo.save(layer);
        }

        return result.consumedUnitCost();
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
                ? totalValue.divide(totalQty, 6, java.math.RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        var stock = stockRepo.findByProductBatchWarehouse(productId, batchId, warehouseId);
        if (stock.isPresent()) {
            var s = stock.get();
            stockRepo.save(new InventoryStock(
                    s.id(), s.productId(), s.batchId(), s.warehouseId(),
                    s.currentQuantity(), s.committedQuantity(), avgCost,
                    s.createdAt(), s.updatedAt()
            ));
        }
    }

    private CostingMethod resolveCostingMethod() {
        return configRepo.findConfig()
                .map(CompanyConfig::costingMethod)
                .map(CostingMethod::fromString)
                .orElse(CostingMethod.FIFO);
    }

    private CostingStrategy strategyFor(CostingMethod method) {
        return STRATEGIES.get(method);
    }
}
