package co.posinvent.application.usecase;

import co.posinvent.domain.model.Batch;
import co.posinvent.domain.model.Batch.BatchStatus;
import co.posinvent.domain.model.InventoryStock;
import co.posinvent.domain.model.MovementType;
import co.posinvent.domain.repository.BatchRepository;
import co.posinvent.domain.repository.StockRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Disposes an expired batch: zeroes its stock, records the kardex movement and
 * closes the batch. The whole unit of work runs inside a fresh, retryable
 * transaction provided by {@link OptimisticConcurrencyExecutor}.
 */
@Service
public class DisposeExpiredBatchUseCase {

    private static final Logger log = LoggerFactory.getLogger(DisposeExpiredBatchUseCase.class);

    private final StockRepository stockRepository;
    private final BatchRepository batchRepository;
    private final RecordMovementUseCase recordMovement;
    private final OptimisticConcurrencyExecutor concurrencyExecutor;

    public DisposeExpiredBatchUseCase(
            StockRepository stockRepository,
            BatchRepository batchRepository,
            RecordMovementUseCase recordMovement,
            OptimisticConcurrencyExecutor concurrencyExecutor
    ) {
        this.stockRepository = stockRepository;
        this.batchRepository = batchRepository;
        this.recordMovement = recordMovement;
        this.concurrencyExecutor = concurrencyExecutor;
    }

    public void execute(UUID batchId, UUID productId, UUID warehouseId, BigDecimal remainingQty) {
        concurrencyExecutor.execute(() -> {
            dispose(batchId, productId, warehouseId, remainingQty);
            return null;
        });
    }

    private void dispose(UUID batchId, UUID productId, UUID warehouseId, BigDecimal remainingQty) {
        // Decrement stock to 0
        var stock = stockRepository.findByProductBatchWarehouse(productId, batchId, warehouseId);
        if (stock.isPresent()) {
            var s = stock.get();
            stockRepository.save(new InventoryStock(
                    s.id(), s.productId(), s.batchId(), s.warehouseId(),
                    BigDecimal.ZERO, s.committedQuantity(), s.unitCost(),
                    s.createdAt(), null
            ));
        }

        // Record kardex
        recordMovement.record(
                productId, batchId, warehouseId,
                MovementType.DISPOSAL,
                remainingQty, BigDecimal.ZERO,
                remainingQty, BigDecimal.ZERO,
                "EXPIRATION", batchId,
                "Vencimiento automático — lote #" + batchId
        );

        // Close batch
        batchRepository.findById(batchId).ifPresent(b -> {
            batchRepository.save(new Batch(
                    b.id(), b.productId(), b.supplierId(), b.warehouseId(), b.entryDate(),
                    b.initialWeight(), b.purchaseCost(), BatchStatus.CLOSED,
                    b.notes(), b.expirationDate(), b.createdBy(),
                    b.createdAt(), null, b.updatedBy(), b.sourceReceiptId(), b.ocId(),
                    null, null, null,
                    b.parentBatchId(), b.batchType(), b.unitOfMeasureId()
            ));
        });

        log.info("Lote {} dispuesto por vencimiento. Cantidad: {}", batchId, remainingQty);
    }
}
