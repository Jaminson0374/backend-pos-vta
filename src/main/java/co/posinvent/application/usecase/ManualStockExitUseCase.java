package co.posinvent.application.usecase;

import co.posinvent.application.dto.ManualStockExitRequest;
import co.posinvent.domain.exception.BusinessException;
import co.posinvent.domain.exception.ResourceNotFoundException;
import co.posinvent.domain.model.InventoryStock;
import co.posinvent.domain.model.MovementType;
import co.posinvent.domain.repository.ProductRepository;
import co.posinvent.domain.repository.StockRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;

@Service
public class ManualStockExitUseCase {

    private final StockRepository stockRepo;
    private final ProductRepository productRepo;
    private final RecordMovementUseCase recordMovement;
    private final OptimisticConcurrencyExecutor concurrencyExecutor;

    public ManualStockExitUseCase(
            StockRepository stockRepo,
            ProductRepository productRepo,
            RecordMovementUseCase recordMovement,
            OptimisticConcurrencyExecutor concurrencyExecutor
    ) {
        this.stockRepo = stockRepo;
        this.productRepo = productRepo;
        this.recordMovement = recordMovement;
        this.concurrencyExecutor = concurrencyExecutor;
    }

    public void execute(ManualStockExitRequest request) {
        concurrencyExecutor.execute(() -> {
            productRepo.findById(request.productId())
                    .orElseThrow(() -> new ResourceNotFoundException("Producto", request.productId()));

            var existing = stockRepo.findByProductBatchWarehouse(
                    request.productId(), request.batchId(), request.warehouseId());

            if (existing.isEmpty()) {
                throw new BusinessException("STOCK_NOT_FOUND",
                        "No existe stock para el producto en esta bodega.");
            }

            var stock = existing.get();
            if (stock.currentQuantity().compareTo(request.quantity()) < 0) {
                throw new BusinessException("INSUFFICIENT_STOCK",
                        "Stock insuficiente. Disponible: " + stock.currentQuantity()
                        + ", Requerido: " + request.quantity());
            }

            var previousQty = stock.currentQuantity();
            var newQty = previousQty.subtract(request.quantity());

            stockRepo.save(new InventoryStock(
                    stock.id(), stock.productId(), stock.batchId(), stock.warehouseId(),
                    newQty, stock.committedQuantity(), stock.unitCost(),
                    stock.createdAt(), stock.updatedAt()
            ));

            recordMovement.record(
                    request.productId(), request.batchId(), request.warehouseId(),
                    MovementType.EXIT,
                    request.quantity(), stock.unitCost(),
                    previousQty, newQty,
                    "MANUAL_EXIT", null,
                    request.reason()
            );

            productRepo.recalculateTotalStock(request.productId());
            return null;
        });
    }
}
