package co.posinvent.application.usecase;

import co.posinvent.application.dto.BatchItemResponse;
import co.posinvent.application.dto.ProduceRequest;
import co.posinvent.application.dto.ProduceResponse;
import co.posinvent.application.port.in.FormulaProductionPort;
import co.posinvent.domain.exception.ResourceNotFoundException;
import co.posinvent.domain.model.*;
import co.posinvent.domain.repository.*;
import co.posinvent.domain.service.BomExploder;
import co.posinvent.domain.service.FefoPicker;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class FormulaProductionUseCase implements FormulaProductionPort {

    private final ProductFormulaRepository formulaRepo;
    private final KardexRepository kardexRepo;
    private final RecordMovementUseCase recordMovementUseCase;
    private final ProductionBatchRepository batchRepo;
    private final CompanyConfigRepository configRepo;
    private final ProductRepository productRepo;
    private final BomExploder bomExploder;
    private final FefoPicker fefoPicker;
    private final StockRepository stockRepository;
    private final CostingOrchestrator costingService;
    private final ThirdPartyRepository thirdPartyRepo;
    private final BatchRepository batchInventoryRepo;

    public FormulaProductionUseCase(
            ProductFormulaRepository formulaRepo,
            KardexRepository kardexRepo,
            RecordMovementUseCase recordMovementUseCase,
            ProductionBatchRepository batchRepo,
            CompanyConfigRepository configRepo,
            ProductRepository productRepo,
            BomExploder bomExploder,
            FefoPicker fefoPicker,
            StockRepository stockRepository,
            CostingOrchestrator costingService,
            ThirdPartyRepository thirdPartyRepo,
            BatchRepository batchInventoryRepo) {
        this.formulaRepo = formulaRepo;
        this.kardexRepo = kardexRepo;
        this.recordMovementUseCase = recordMovementUseCase;
        this.batchRepo = batchRepo;
        this.configRepo = configRepo;
        this.productRepo = productRepo;
        this.bomExploder = bomExploder;
        this.fefoPicker = fefoPicker;
        this.stockRepository = stockRepository;
        this.costingService = costingService;
        this.thirdPartyRepo = thirdPartyRepo;
        this.batchInventoryRepo = batchInventoryRepo;
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public ProduceResponse produce(ProduceRequest request, UUID operatorId) {
        var parentProduct = productRepo.findById(request.formulaProductId())
                .orElseThrow(() -> new IllegalArgumentException("Producto no encontrado"));

        // Validate product is configured for in-house manufacturing
        if (!parentProduct.manufacturedInHouse()) {
            throw new IllegalArgumentException("El producto no está configurado como fabricado internamente");
        }

        // Step 1: Explode BOM — stocked intermediates are consumed as leaves, otherwise recurse to raw materials
        var explodedComponents = bomExploder.explode(request.formulaProductId(), request.warehouseId(), request.quantity());
        if (explodedComponents.isEmpty()) {
            throw new IllegalArgumentException("El producto no tiene fórmula definida");
        }

        // Step 2: Merge duplicate raw materials (same product appears at multiple levels)
        var merged = new java.util.HashMap<UUID, BigDecimal>();
        for (var ec : explodedComponents) {
            merged.merge(ec.productId(), ec.totalQuantity(), BigDecimal::add);
        }

        // Step 3: Validate stock for each merged raw material using FEFO
        var components = new ArrayList<ComponentCalc>();
        var materialAllocations = new java.util.HashMap<UUID, java.util.List<BatchAllocation>>();
        for (var entry : merged.entrySet()) {
            UUID productId = entry.getKey();
            BigDecimal requiredQty = entry.getValue();

            var component = productRepo.findById(productId)
                    .orElseThrow(() -> new IllegalArgumentException("Componente no encontrado: " + productId));

            java.util.List<BatchAllocation> allocations = fefoPicker.pick(productId, request.warehouseId(), requiredQty);
            materialAllocations.put(productId, allocations);
            components.add(new ComponentCalc(component, requiredQty, allocations));
        }

        // Step 4: Calculate costs
        var config = configRepo.findConfig();
        String costingMethod = config.map(CompanyConfig::costingMethod)
                .filter(m -> m != null && !m.isBlank())
                .orElse("FIFO");

        String overheadAllocationBase = config.map(CompanyConfig::overheadAllocationBase)
                .filter(b -> b != null && !b.isBlank())
                .orElse("MOD");

        BigDecimal overheadRate = config.map(CompanyConfig::overheadRate)
                .orElse(BigDecimal.ZERO);

        BigDecimal mpdTotal = BigDecimal.ZERO;
        for (var comp : components) {
            BigDecimal unitCost = kardexRepo.getUnitCost(comp.component.id(), request.warehouseId(), costingMethod)
                    .orElse(BigDecimal.ZERO);
            comp.unitCost = unitCost;
            comp.totalCost = comp.requiredQty.multiply(unitCost);
            mpdTotal = mpdTotal.add(comp.totalCost);
        }

        BigDecimal laborCost = request.laborCost() != null ? request.laborCost() : BigDecimal.ZERO;
        BigDecimal overheadCost;
        if (request.overheadCost() != null) {
            overheadCost = request.overheadCost();
        } else {
            if ("MOD".equalsIgnoreCase(overheadAllocationBase)) {
                overheadCost = laborCost.multiply(overheadRate).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
            } else {
                overheadCost = mpdTotal.multiply(overheadRate).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
            }
        }

        BigDecimal totalCost = mpdTotal.add(laborCost).add(overheadCost);
        BigDecimal unitCost = totalCost.divide(request.quantity(), 4, RoundingMode.HALF_UP);

        // Step 5: Create ProductionBatch
        var batch = new ProductionBatch(
                null, request.formulaProductId(), request.quantity(), request.quantity(),
                mpdTotal, laborCost, overheadCost, totalCost, unitCost,
                BigDecimal.ZERO, BigDecimal.ZERO, request.notes(), operatorId, null, null
        );
        var savedBatch = batchRepo.save(batch);

        // Step 5a-5e: Create inventory Batch + InventoryStock + cost layers for finished product
        var systemSupplier = resolveSystemSupplier();
        var inventoryBatch = batchInventoryRepo.save(new Batch(
                null,
                request.formulaProductId(),
                systemSupplier.id(),
                request.warehouseId(),
                LocalDate.now(),
                request.quantity(),
                totalCost,
                Batch.BatchStatus.OPEN,
                "Producción lote " + savedBatch.id(),
                request.expirationDate(),
                operatorId,
                null, null, null, null, null,
                null, null, null,
                null, BatchType.STANDARD, null
        ));

        stockRepository.save(new InventoryStock(
                null,
                request.formulaProductId(),
                inventoryBatch.id(),
                request.warehouseId(),
                request.quantity(),
                BigDecimal.ZERO,
                unitCost,
                null, null
        ));

        costingService.resolveCostOnEntry(
                request.formulaProductId(),
                inventoryBatch.id(),
                request.warehouseId(),
                request.quantity(),
                unitCost,
                null
        );

        // Update production batch with the inventory batch ID
        savedBatch = batchRepo.save(new ProductionBatch(
                savedBatch.id(),
                savedBatch.formulaId(),
                savedBatch.quantityProduced(),
                savedBatch.expectedQuantity(),
                savedBatch.directMaterialCost(),
                savedBatch.directLaborCost(),
                savedBatch.overheadCost(),
                savedBatch.totalCost(),
                savedBatch.unitCost(),
                savedBatch.shrinkageQuantity(),
                savedBatch.shrinkageCost(),
                savedBatch.notes(),
                savedBatch.createdBy(),
                savedBatch.createdAt(),
                inventoryBatch.id()
        ));

        // Step 6: Record kardex — consume raw materials per FEFO batch + decrement InventoryStock
        var items = new ArrayList<BatchItemResponse>();
        for (var comp : components) {
            var allocations = materialAllocations.get(comp.component.id());
            for (var alloc : allocations) {
                // Decrement real InventoryStock
                var stock = stockRepository.findByProductBatchWarehouse(
                                comp.component.id(), alloc.batchId(), request.warehouseId())
                        .orElseThrow(() -> new ResourceNotFoundException("Stock", comp.component.id()));

                var previousQty = stock.currentQuantity();
                var newQty = previousQty.subtract(alloc.quantity());

                stockRepository.save(new InventoryStock(
                        stock.id(), stock.productId(), stock.batchId(), stock.warehouseId(),
                        newQty, stock.committedQuantity(), stock.unitCost(),
                        stock.createdAt(), null));

                // Record kardex movement with real batchId
                var movement = recordMovementUseCase.record(
                        comp.component.id(),
                        alloc.batchId(),
                        request.warehouseId(),
                        MovementType.PRODUCTION_CONSUMPTION,
                        alloc.quantity().negate(),
                        alloc.unitCost(),
                        previousQty,
                        newQty,
                        "PRODUCTION",
                        savedBatch.id(),
                        "Consumo producción #" + savedBatch.id()
                );

                costingService.resolveCostOnExit(comp.component.id(), alloc.batchId(), request.warehouseId(), alloc.quantity());

                items.add(new BatchItemResponse(
                        comp.component.id(),
                        comp.component.name(),
                        comp.requiredQty,
                        alloc.quantity(),
                        alloc.unitCost(),
                        alloc.quantity().multiply(alloc.unitCost()),
                        movement.id()
                ));
            }

            // Recalculate total stock for material after all batches decremented
            productRepo.recalculateTotalStock(comp.component.id());
        }

        // Step 7: Record kardex output for finished product — use real inventory batch ID
        recordMovementUseCase.record(
                request.formulaProductId(),
                inventoryBatch.id(),
                request.warehouseId(),
                MovementType.PRODUCTION_OUTPUT,
                request.quantity(),
                unitCost,
                kardexRepo.getCurrentStock(request.formulaProductId(), request.warehouseId()),
                kardexRepo.getCurrentStock(request.formulaProductId(), request.warehouseId()).add(request.quantity()),
                "PRODUCTION_BATCH",
                savedBatch.id(),
                "Producción del lote " + savedBatch.id()
        );

        // Step 8: Recalculate stock for finished product
        productRepo.recalculateTotalStock(request.formulaProductId());

        return new ProduceResponse(
                savedBatch.id(),
                inventoryBatch.id(),
                parentProduct.name(),
                request.quantity(),
                mpdTotal,
                laborCost,
                overheadCost,
                totalCost,
                unitCost,
                BigDecimal.ZERO,
                items
        );
    }

    private ThirdParty resolveSystemSupplier() {
        return thirdPartyRepo.findByNumIdentification("000000000-0")
                .orElseThrow(() -> new IllegalStateException(
                        "Proveedor sistema PRODUCCIÓN INTERNA faltante. Ejecute migración V95."));
    }

    private static class ComponentCalc {
        final Product component;
        final BigDecimal requiredQty;
        final java.util.List<BatchAllocation> allocations;
        BigDecimal unitCost = BigDecimal.ZERO;
        BigDecimal totalCost = BigDecimal.ZERO;

        ComponentCalc(Product component, BigDecimal requiredQty, java.util.List<BatchAllocation> allocations) {
            this.component = component;
            this.requiredQty = requiredQty;
            this.allocations = allocations;
        }
    }
}
