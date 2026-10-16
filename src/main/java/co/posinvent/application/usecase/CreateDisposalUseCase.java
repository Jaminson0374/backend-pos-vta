package co.posinvent.application.usecase;

import co.posinvent.application.dto.DisposalRequest;
import co.posinvent.application.dto.DisposalResponse;
import co.posinvent.application.port.in.CreateDisposalPort;
import co.posinvent.domain.exception.BusinessException;
import co.posinvent.domain.exception.ResourceNotFoundException;
import co.posinvent.domain.model.*;
import co.posinvent.domain.repository.*;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Service
public class CreateDisposalUseCase implements CreateDisposalPort {

    private final WasteDisposalRepository disposalRepo;
    private final StockRepository stockRepo;
    private final ProductRepository productRepo;
    private final RecordMovementUseCase recordMovement;
    private final OptimisticConcurrencyExecutor concurrencyExecutor;
    private final ApplicationEventPublisher eventPublisher;

    public CreateDisposalUseCase(WasteDisposalRepository disposalRepo, StockRepository stockRepo,
                                  ProductRepository productRepo, RecordMovementUseCase recordMovement,
                                  OptimisticConcurrencyExecutor concurrencyExecutor,
                                  ApplicationEventPublisher eventPublisher) {
        this.disposalRepo = disposalRepo; this.stockRepo = stockRepo;
        this.productRepo = productRepo; this.recordMovement = recordMovement;
        this.concurrencyExecutor = concurrencyExecutor;
        this.eventPublisher = eventPublisher;
    }

    public DisposalResponse execute(DisposalRequest request, UUID operatorId) {
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

            var disposal = disposalRepo.save(new WasteDisposal(null, request.productId(), request.batchId(),
                    request.warehouseId(), type, request.quantity(), stock.unitCost(), request.reason(),
                    request.officialDocument(), request.disposalDate(), null, operatorId, null));

            recordMovement.record(request.productId(), request.batchId(), request.warehouseId(),
                    MovementType.DISPOSAL, request.quantity(), stock.unitCost(),
                    stock.currentQuantity(), newQty, "DISPOSAL", disposal.id(), request.reason());

            productRepo.recalculateTotalStock(request.productId());

            // Publish the accounting event (WU-36c): write off the disposed goods at their stock
            // cost. Published inside the transaction so the AFTER_COMMIT listener posts once the
            // disposal is persisted.
            var totalCost = request.quantity().multiply(stock.unitCost());
            var entryDate = request.disposalDate() != null ? request.disposalDate() : LocalDate.now();
            eventPublisher.publishEvent(new DisposalAccountedEvent(
                    this, disposal.id(), disposal.id().toString(), totalCost, entryDate));

            return DisposalResponse.from(disposal);
        });
    }
}
