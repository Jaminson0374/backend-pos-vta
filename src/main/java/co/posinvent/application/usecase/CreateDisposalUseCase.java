package co.posinvent.application.usecase;

import co.posinvent.application.dto.DisposalRequest;
import co.posinvent.application.dto.DisposalResponse;
import co.posinvent.domain.exception.BusinessException;
import co.posinvent.domain.exception.ResourceNotFoundException;
import co.posinvent.domain.model.*;
import co.posinvent.domain.repository.*;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;

@Service
public class CreateDisposalUseCase {

    private final StockDisposalRepository disposalRepo;
    private final StockRepository stockRepo;
    private final ProductRepository productRepo;
    private final RecordMovementUseCase recordMovement;
    private final OptimisticConcurrencyExecutor concurrencyExecutor;

    public CreateDisposalUseCase(StockDisposalRepository disposalRepo, StockRepository stockRepo,
                                  ProductRepository productRepo, RecordMovementUseCase recordMovement,
                                  OptimisticConcurrencyExecutor concurrencyExecutor) {
        this.disposalRepo = disposalRepo; this.stockRepo = stockRepo;
        this.productRepo = productRepo; this.recordMovement = recordMovement;
        this.concurrencyExecutor = concurrencyExecutor;
    }

    public DisposalResponse execute(DisposalRequest request) {
        return concurrencyExecutor.execute(() -> {
            var type = DisposalType.valueOf(request.disposalType().toUpperCase());
            productRepo.findById(request.productId())
                    .orElseThrow(() -> new ResourceNotFoundException("Producto", request.productId()));

            var existing = stockRepo.findByProductBatchWarehouse(
                    request.productId(), request.batchId(), request.warehouseId());
            if (existing.isEmpty()) throw new BusinessException("NO_STOCK", "No hay stock para decomisar.");

            var stock = existing.get();
            if (stock.currentQuantity().compareTo(request.quantity()) < 0)
                throw new BusinessException("INSUFFICIENT_STOCK",
                        "Stock insuficiente. Disponible: " + stock.currentQuantity());

            var newQty = stock.currentQuantity().subtract(request.quantity());
            stockRepo.save(new InventoryStock(stock.id(), stock.productId(), stock.batchId(), stock.warehouseId(),
                    newQty, stock.committedQuantity(), stock.unitCost(), stock.createdAt(), stock.updatedAt()));

            var disposal = disposalRepo.save(new StockDisposal(null, request.productId(), request.batchId(),
                    request.warehouseId(), type, request.quantity(), stock.unitCost(), request.reason(), "SYSTEM", null));

            recordMovement.record(request.productId(), request.batchId(), request.warehouseId(),
                    MovementType.DISPOSAL, request.quantity(), stock.unitCost(),
                    stock.currentQuantity(), newQty, "DISPOSAL", disposal.id(), request.reason());

            productRepo.recalculateTotalStock(request.productId());
            return DisposalResponse.from(disposal);
        });
    }
}
