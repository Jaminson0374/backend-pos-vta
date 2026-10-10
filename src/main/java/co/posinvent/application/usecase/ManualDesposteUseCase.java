package co.posinvent.application.usecase;

import co.posinvent.application.dto.ManualDesposteRequest;
import co.posinvent.application.dto.ManualDesposteResponse;
import co.posinvent.domain.exception.BusinessException;
import co.posinvent.domain.exception.ResourceNotFoundException;
import co.posinvent.domain.model.Batch;
import co.posinvent.domain.model.Batch.BatchStatus;
import co.posinvent.domain.model.BatchType;
import co.posinvent.domain.model.Desposte;
import co.posinvent.domain.model.InventoryStock;
import co.posinvent.domain.model.ManualDespostePlan;
import co.posinvent.domain.model.MovementType;
import co.posinvent.domain.repository.BatchRepository;
import co.posinvent.domain.repository.DesposteRepository;
import co.posinvent.domain.repository.ProductRepository;
import co.posinvent.domain.repository.StockRepository;
import co.posinvent.domain.repository.WarehouseRepository;
import co.posinvent.domain.service.ManualDesposteDomainService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

@Service
public class ManualDesposteUseCase {

    private final BatchRepository batchRepository;
    private final ProductRepository productRepository;
    private final WarehouseRepository warehouseRepository;
    private final StockRepository stockRepository;
    private final ManualDesposteDomainService domainService;
    private final RecordMovementUseCase recordMovement;
    private final DesposteRepository desposteRepository;

    // Injected via field (not constructor) so the existing 6-arg constructor
    // used by unit tests keeps compiling. When null (plain unit tests), the
    // unit of work runs without the retry wrapper.
    @Autowired
    private OptimisticConcurrencyExecutor concurrencyExecutor;

    public ManualDesposteUseCase(
            BatchRepository batchRepository,
            ProductRepository productRepository,
            WarehouseRepository warehouseRepository,
            StockRepository stockRepository,
            ManualDesposteDomainService domainService,
            RecordMovementUseCase recordMovement,
            DesposteRepository desposteRepository
    ) {
        this.batchRepository = batchRepository;
        this.productRepository = productRepository;
        this.warehouseRepository = warehouseRepository;
        this.stockRepository = stockRepository;
        this.domainService = domainService;
        this.recordMovement = recordMovement;
        this.desposteRepository = desposteRepository;
    }

    public ManualDesposteResponse processManual(ManualDesposteRequest request) {
        if (concurrencyExecutor != null) {
            return concurrencyExecutor.execute(() -> doProcessManual(request));
        }
        return doProcessManual(request);
    }

    private ManualDesposteResponse doProcessManual(ManualDesposteRequest request) {
        var batch = batchRepository.findById(request.sourceBatchId())
                .orElseThrow(() -> new ResourceNotFoundException("Lote", request.sourceBatchId()));

        validateCutReferences(request);

        var plan = domainService.planForExistingBatch(batch, toDomainCommand(request));

        // --- Step 1: Create child batches and per-child InventoryStock ---
        List<UUID> childBatchIds = new ArrayList<>();
        for (var childPlan : plan.childBatchPlans()) {
            var childBatch = createChildBatch(batch, childPlan);
            childBatchIds.add(childBatch.id());
            createChildStock(childBatch, childPlan);
            recordChildOutputMovement(childBatch, childPlan);
        }

        // --- Step 2: Decrement parent batch stock ---
        var consumedWeight = plan.massBalance().inputWeight();
        decrementParentStock(batch, consumedWeight);

        // --- Step 3: Record PRODUCTION_CONSUMPTION for the parent ---
        var parentStock = stockRepository
                .findByProductBatchWarehouse(batch.productId(), batch.id(), batch.warehouseId())
                .orElseThrow(() -> new BusinessException(
                        "PARENT_STOCK_NOT_FOUND",
                        "No se encontró stock para el lote padre: " + batch.id()));

        recordMovement.record(
                batch.productId(),
                batch.id(),
                batch.warehouseId(),
                MovementType.PRODUCTION_CONSUMPTION,
                consumedWeight,
                parentStock.unitCost(),
                parentStock.currentQuantity().add(consumedWeight), // previousQty before decrement
                parentStock.currentQuantity(),
                "DESPOSTE",
                batch.id(),
                "Desposte — consumo en producción");

        productRepository.recalculateTotalStock(batch.productId());

        // --- Step 4: Transition parent batch ---
        var newStatus = resolveParentStatus(parentStock.currentQuantity());
        if (newStatus != batch.status()) {
            batchRepository.save(new Batch(
                    batch.id(),
                    batch.productId(),
                    batch.supplierId(),
                    batch.warehouseId(),
                    batch.entryDate(),
                    batch.initialWeight(),
                    batch.purchaseCost(),
                    newStatus,
                    batch.notes(),
                    batch.expirationDate(),
                    batch.createdBy(),
                    batch.createdAt(),
                    batch.updatedAt(),
                    batch.updatedBy(),
                    batch.sourceReceiptId(),
                    batch.ocId(),
                    null, null, null,
                    batch.parentBatchId(),
                    batch.batchType(),
                    batch.unitOfMeasureId()));
        }

        // --- Step 5: Persist the desposte header + cuts for the MVM screen ---
        desposteRepository.save(buildDesposte(batch, plan, childBatchIds, request));

        return ManualDesposteResponse.withChildBatches(plan, childBatchIds);
    }

    /**
     * Assembles the persisted desposte snapshot from the executed plan.
     * Cuts keep the exact order of {@code plan.cuts()} so each cut can be
     * linked to the child batch minted at the same position.
     */
    private Desposte buildDesposte(
            Batch batch,
            ManualDespostePlan plan,
            List<UUID> childBatchIds,
            ManualDesposteRequest request
    ) {
        var balance = plan.massBalance();
        var yieldPercentage = resolveYieldPercentage(balance.inputWeight(), balance.totalCutsWeight());

        var cuts = new ArrayList<Desposte.DesposteCut>(plan.cuts().size());
        for (int index = 0; index < plan.cuts().size(); index++) {
            var cut = plan.cuts().get(index);
            var childBatchId = index < childBatchIds.size() ? childBatchIds.get(index) : null;
            cuts.add(new Desposte.DesposteCut(
                    null,
                    cut.productId(),
                    cut.warehouseId(),
                    childBatchId,
                    cut.weight(),
                    cut.suggestedSalePrice(),
                    cut.commercialValue(),
                    cut.allocatedCost(),
                    cut.unitCost(),
                    cut.expirationDate()));
        }

        return new Desposte(
                null,
                batch.id(),
                batch.productId(),
                batch.warehouseId(),
                balance.inputWeight(),
                balance.totalCutsWeight(),
                balance.wasteWeight(),
                balance.shrinkWeight(),
                balance.deviation(),
                balance.tolerance(),
                balance.withinTolerance(),
                yieldPercentage,
                plan.totalCommercialValue(),
                plan.totalAllocatedCost(),
                request.notes(),
                batch.createdBy() != null ? batch.createdBy().toString() : null,
                null,
                cuts);
    }

    /**
     * Yield = total cuts weight / input weight * 100, guarded against a zero input.
     */
    private BigDecimal resolveYieldPercentage(BigDecimal inputWeight, BigDecimal totalCutsWeight) {
        if (inputWeight == null || inputWeight.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        }
        return totalCutsWeight
                .divide(inputWeight, 6, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"))
                .setScale(4, RoundingMode.HALF_UP);
    }

    // ── Helpers ────────────────────────────────────────────────────────

    private void validateCutReferences(ManualDesposteRequest request) {
        var productIds = new LinkedHashSet<UUID>();
        var warehouseIds = new LinkedHashSet<UUID>();

        for (var cut : request.cuts()) {
            productIds.add(cut.productId());
            warehouseIds.add(cut.warehouseId());
        }

        for (var productId : productIds) {
            var product = productRepository.findById(productId)
                    .orElseThrow(() -> new ResourceNotFoundException("Producto", productId));

            if (!product.active()) {
                throw new BusinessException(
                        "INACTIVE_DESPOSTE_PRODUCT",
                        "El producto resultante esta inactivo: " + productId);
            }

            if (!product.inventoriable()) {
                throw new BusinessException(
                        "NON_INVENTORIABLE_DESPOSTE_PRODUCT",
                        "El producto resultante debe ser inventariable: " + productId);
            }
        }

        for (var warehouseId : warehouseIds) {
            var warehouse = warehouseRepository.findById(warehouseId)
                    .orElseThrow(() -> new ResourceNotFoundException("Bodega", warehouseId));

            if (!warehouse.active()) {
                throw new BusinessException(
                        "INACTIVE_DESPOSTE_WAREHOUSE",
                        "La bodega destino esta inactiva: " + warehouseId);
            }
        }
    }

    private ManualDespostePlan.Command toDomainCommand(ManualDesposteRequest request) {
        return new ManualDespostePlan.Command(
                request.sourceBatchId(),
                request.sourceType(),
                request.manualJustification(),
                request.wasteWeight(),
                request.shrinkWeight(),
                request.notes(),
                request.cuts().stream()
                        .map(cut -> new ManualDespostePlan.ManualDesposteCutCommand(
                                cut.productId(),
                                cut.warehouseId(),
                                cut.weight(),
                                cut.suggestedSalePrice(),
                                cut.expirationDate()))
                        .toList());
    }

    /**
     * Creates a child Batch record with batchType=CHILD, parentBatchId pointing to the
     * source, and inheriting supplierId, entryDate, and unitOfMeasureId from the parent.
     */
    private Batch createChildBatch(Batch parent, ManualDespostePlan.ChildBatchPlan plan) {
        var child = new Batch(
                null,                           // id — generated by the database
                plan.productId(),
                plan.supplierId(),
                plan.warehouseId(),
                plan.entryDate(),
                plan.initialWeight(),
                plan.purchaseCost(),
                BatchStatus.OPEN,
                plan.notes(),
                plan.expirationDate(),
                null,                           // createdBy — filled by JPA audit
                null,                           // createdAt
                null,                           // updatedAt
                null,                           // updatedBy
                null,                           // sourceReceiptId
                null,                           // ocId
                null, null, null,              // enriched display fields
                parent.id(),                    // parentBatchId
                BatchType.CHILD,
                parent.unitOfMeasureId());
        return batchRepository.save(child);
    }

    /**
     * Creates an InventoryStock entry for a newly minted child batch.
     * If stock already exists at this (product, batch, warehouse) key, the quantity
     * is accumulated and the average unit cost is recalculated.
     */
    private void createChildStock(Batch childBatch, ManualDespostePlan.ChildBatchPlan plan) {
        var unitCost = plan.initialWeight().compareTo(BigDecimal.ZERO) > 0
                ? plan.purchaseCost().divide(plan.initialWeight(), 6, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        var existing = stockRepository.findByProductBatchWarehouse(
                plan.productId(),
                childBatch.id(),
                plan.warehouseId());

        if (existing.isPresent()) {
            var s = existing.get();
            var existingAllocated = s.currentQuantity().multiply(s.unitCost());
            var newAllocated = plan.initialWeight().multiply(unitCost);
            var newQty = s.currentQuantity().add(plan.initialWeight());
            var avgUnitCost = existingAllocated.add(newAllocated)
                    .divide(newQty, 6, RoundingMode.HALF_UP);

            stockRepository.save(new InventoryStock(
                    s.id(), s.productId(), s.batchId(), s.warehouseId(),
                    newQty, s.committedQuantity(), avgUnitCost,
                    s.createdAt(), null));
        } else {
            stockRepository.save(new InventoryStock(
                    null,
                    plan.productId(),
                    childBatch.id(),
                    plan.warehouseId(),
                    plan.initialWeight(),
                    BigDecimal.ZERO,
                    unitCost,
                    null, null));
        }
        productRepository.recalculateTotalStock(plan.productId());
    }

    /**
     * Records a PRODUCTION_OUTPUT kardex movement for a child batch.
     */
    private void recordChildOutputMovement(Batch childBatch, ManualDespostePlan.ChildBatchPlan plan) {
        var unitCost = plan.initialWeight().compareTo(BigDecimal.ZERO) > 0
                ? plan.purchaseCost().divide(plan.initialWeight(), 6, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        recordMovement.record(
                plan.productId(),
                childBatch.id(),
                plan.warehouseId(),
                MovementType.PRODUCTION_OUTPUT,
                plan.initialWeight(),
                unitCost,
                BigDecimal.ZERO,
                plan.initialWeight(),
                "DESPOSTE",
                childBatch.id(),
                "Desposte — producción de corte");
    }

    /**
     * Decrements the parent batch's InventoryStock by the consumed weight.
     * Throws {@link BusinessException} if the parent stock is missing or insufficient.
     */
    private void decrementParentStock(Batch batch, BigDecimal consumedWeight) {
        var stock = stockRepository
                .findByProductBatchWarehouse(batch.productId(), batch.id(), batch.warehouseId())
                .orElseThrow(() -> new BusinessException(
                        "PARENT_STOCK_NOT_FOUND",
                        "No se encontró stock para el lote padre: " + batch.id()));

        var newQty = stock.currentQuantity().subtract(consumedWeight);
        if (newQty.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException(
                    "INSUFFICIENT_PARENT_STOCK",
                    "Stock insuficiente en lote padre " + batch.id()
                    + ": disponible " + stock.currentQuantity().setScale(3, RoundingMode.HALF_UP)
                    + " vs consumido " + consumedWeight.setScale(3, RoundingMode.HALF_UP));
        }

        stockRepository.save(new InventoryStock(
                stock.id(),
                stock.productId(),
                stock.batchId(),
                stock.warehouseId(),
                newQty,
                stock.committedQuantity(),
                stock.unitCost(),
                stock.createdAt(),
                null));
    }

    /**
     * Resolves the new batch status based on remaining stock.
     * stock ≈ 0 → CLOSED, stock > 0 → PROCESSING.
     */
    private BatchStatus resolveParentStatus(BigDecimal remainingQty) {
        return remainingQty.compareTo(new BigDecimal("0.0005")) <= 0
                ? BatchStatus.CLOSED
                : BatchStatus.PROCESSING;
    }
}
