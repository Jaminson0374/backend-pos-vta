package co.posinvent.application.usecase;

import co.posinvent.application.dto.ManualStockEntryRequest;
import co.posinvent.application.port.in.ManualStockEntryPort;
import co.posinvent.domain.exception.ResourceNotFoundException;
import co.posinvent.domain.model.InventoryStock;
import co.posinvent.domain.model.MovementType;
import co.posinvent.domain.repository.ProductRepository;
import co.posinvent.domain.repository.StockRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;

@Service
public class ManualStockEntryUseCase implements ManualStockEntryPort {

    private final StockRepository stockRepo;
    private final ProductRepository productRepo;
    private final RecordMovementUseCase recordMovement;
    private final OptimisticConcurrencyExecutor concurrencyExecutor;

    public ManualStockEntryUseCase(
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

    public void execute(ManualStockEntryRequest request) {
        concurrencyExecutor.execute(() -> {
            productRepo.findById(request.productId())
                    .orElseThrow(() -> new ResourceNotFoundException("Producto", request.productId()));

            var existing = stockRepo.findByProductBatchWarehouse(
                    request.productId(), request.batchId(), request.warehouseId());

            BigDecimal previousQty;
            BigDecimal newQty;
            BigDecimal unitCost = request.unitCost() != null ? request.unitCost() : BigDecimal.ZERO;

            if (existing.isPresent()) {
                var stock = existing.get();
                previousQty = stock.currentQuantity();
                newQty = previousQty.add(request.quantity());
                stockRepo.save(new InventoryStock(
                        stock.id(), stock.productId(), stock.batchId(), stock.warehouseId(),
                        newQty, stock.committedQuantity(),
                        unitCost.compareTo(BigDecimal.ZERO) > 0 ? unitCost : stock.unitCost(),
                        stock.createdAt(), stock.updatedAt()
                ));
            } else {
                previousQty = BigDecimal.ZERO;
                newQty = request.quantity();
                stockRepo.save(new InventoryStock(
                        null, request.productId(), request.batchId(), request.warehouseId(),
                        newQty, BigDecimal.ZERO, unitCost, null, null
                ));
            }

            recordMovement.record(
                    request.productId(), request.batchId(), request.warehouseId(),
                    MovementType.ENTRY,
                    request.quantity(), unitCost,
                    previousQty, newQty,
                    "MANUAL_ENTRY", null,
                    request.notes()
            );

            productRepo.recalculateTotalStock(request.productId());
            return null;
        });
    }
}
