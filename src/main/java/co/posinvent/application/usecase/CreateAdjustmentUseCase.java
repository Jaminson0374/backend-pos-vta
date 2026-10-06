package co.posinvent.application.usecase;

import co.posinvent.application.annotation.Auditable;
import co.posinvent.application.dto.AdjustmentRequest;
import co.posinvent.application.dto.AdjustmentResponse;
import co.posinvent.domain.exception.BusinessException;
import co.posinvent.domain.exception.ResourceNotFoundException;
import co.posinvent.domain.model.*;
import co.posinvent.domain.repository.ProductRepository;
import co.posinvent.domain.repository.StockAdjustmentRepository;
import co.posinvent.domain.repository.StockRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;

@Service
public class CreateAdjustmentUseCase {

    private final StockAdjustmentRepository adjustmentRepo;
    private final StockRepository stockRepo;
    private final ProductRepository productRepo;
    private final RecordMovementUseCase recordMovement;
    private final OptimisticConcurrencyExecutor concurrencyExecutor;

    public CreateAdjustmentUseCase(
            StockAdjustmentRepository adjustmentRepo,
            StockRepository stockRepo,
            ProductRepository productRepo,
            RecordMovementUseCase recordMovement,
            OptimisticConcurrencyExecutor concurrencyExecutor
    ) {
        this.adjustmentRepo = adjustmentRepo;
        this.stockRepo = stockRepo;
        this.productRepo = productRepo;
        this.recordMovement = recordMovement;
        this.concurrencyExecutor = concurrencyExecutor;
    }

    @Auditable(entityType = "ADJUSTMENT", action = "CREATE")
    public AdjustmentResponse execute(AdjustmentRequest request) {
        return concurrencyExecutor.execute(() -> {
            var adjustmentType = AdjustmentType.valueOf(request.adjustmentType().toUpperCase());

            productRepo.findById(request.productId())
                    .orElseThrow(() -> new ResourceNotFoundException("Producto", request.productId()));

            // Find current stock
            var existingStock = stockRepo.findByProductBatchWarehouse(
                    request.productId(), request.batchId(), request.warehouseId());

            BigDecimal previousQty;
            BigDecimal unitCost;
            InventoryStock stock;

            if (existingStock.isPresent()) {
                stock = existingStock.get();
                previousQty = stock.currentQuantity();
                unitCost = stock.unitCost();

                // Update stock to new quantity
                stockRepo.save(new InventoryStock(
                        stock.id(), stock.productId(), stock.batchId(), stock.warehouseId(),
                        request.quantityAfter(),
                        stock.committedQuantity(),
                        unitCost,
                        stock.createdAt(),
                        stock.updatedAt()
                ));
            } else {
                previousQty = BigDecimal.ZERO;
                unitCost = BigDecimal.ZERO;

                if (request.quantityAfter().compareTo(BigDecimal.ZERO) > 0) {
                    stockRepo.save(new InventoryStock(
                            null, request.productId(), request.batchId(), request.warehouseId(),
                            request.quantityAfter(),
                            BigDecimal.ZERO, BigDecimal.ZERO,
                            null, null
                    ));
                } else {
                    throw new BusinessException("ADJ_NO_STOCK",
                            "No existe stock para ajustar a negativo.");
                }
            }

            var adjustment = new StockAdjustment(
                    null,
                    request.productId(),
                    request.batchId(),
                    request.warehouseId(),
                    adjustmentType,
                    previousQty,
                    request.quantityAfter(),
                    unitCost,
                    request.reason(),
                    "SYSTEM",
                    null
            );
            var saved = adjustmentRepo.save(adjustment);

            // Record in Kardex (use signed delta, not abs)
            BigDecimal delta = request.quantityAfter().subtract(previousQty);
            recordMovement.record(
                    request.productId(), request.batchId(), request.warehouseId(),
                    MovementType.ADJUSTMENT,
                    delta, unitCost,
                    previousQty, request.quantityAfter(),
                    "ADJUSTMENT", saved.id(),
                    request.adjustmentType() + ": " + request.reason()
            );

            // Recalculate totalStock
            productRepo.recalculateTotalStock(request.productId());

            return AdjustmentResponse.from(saved);
        });
    }
}
