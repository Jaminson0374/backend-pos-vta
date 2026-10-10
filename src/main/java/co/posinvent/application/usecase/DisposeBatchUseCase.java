package co.posinvent.application.usecase;

import co.posinvent.application.port.in.DisposeBatchPort;
import co.posinvent.domain.exception.BusinessException;
import co.posinvent.domain.exception.ResourceNotFoundException;
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
 * Manual disposal of an entire batch: zeroes every stock row of the batch, records the
 * DISPOSAL kardex movement and closes the batch — all in a fresh, retryable transaction.
 */
@Service
public class DisposeBatchUseCase implements DisposeBatchPort {

    private static final Logger log = LoggerFactory.getLogger(DisposeBatchUseCase.class);

    private final BatchRepository batchRepository;
    private final StockRepository stockRepository;
    private final RecordMovementUseCase recordMovement;
    private final OptimisticConcurrencyExecutor concurrencyExecutor;

    public DisposeBatchUseCase(
            BatchRepository batchRepository,
            StockRepository stockRepository,
            RecordMovementUseCase recordMovement,
            OptimisticConcurrencyExecutor concurrencyExecutor
    ) {
        this.batchRepository = batchRepository;
        this.stockRepository = stockRepository;
        this.recordMovement = recordMovement;
        this.concurrencyExecutor = concurrencyExecutor;
    }

    @Override
    public void disposeBatch(UUID batchId) {
        concurrencyExecutor.execute(() -> {
            var batch = batchRepository.findById(batchId)
                    .orElseThrow(() -> new ResourceNotFoundException("Lote", batchId));

            var stocks = stockRepository.findByBatch(batchId);
            if (stocks.isEmpty()) {
                throw new BusinessException("BATCH_NO_STOCK", "El lote no tiene stock registrado");
            }

            for (var stock : stocks) {
                if (stock.currentQuantity().compareTo(BigDecimal.ZERO) > 0) {
                    recordMovement.record(
                            stock.productId(), batchId, stock.warehouseId(),
                            MovementType.DISPOSAL,
                            stock.currentQuantity(), stock.unitCost(),
                            stock.currentQuantity(), BigDecimal.ZERO,
                            "EXPIRATION", batchId,
                            "Disposición manual por vencimiento — lote #" + batchId
                    );
                    stockRepository.save(new InventoryStock(
                            stock.id(), stock.productId(), stock.batchId(), stock.warehouseId(),
                            BigDecimal.ZERO, stock.committedQuantity(), stock.unitCost(),
                            stock.createdAt(), null
                    ));
                }
            }

            batchRepository.save(new Batch(
                    batch.id(), batch.productId(), batch.supplierId(), batch.warehouseId(), batch.entryDate(),
                    batch.initialWeight(), batch.purchaseCost(), BatchStatus.CLOSED,
                    batch.notes(), batch.expirationDate(), batch.createdBy(),
                    batch.createdAt(), null, batch.updatedBy(), batch.sourceReceiptId(), batch.ocId(),
                    null, null, null,
                    batch.parentBatchId(), batch.batchType(), batch.unitOfMeasureId()
            ));

            log.info("Lote {} dispuesto por vencimiento (manual).", batchId);
            return null;
        });
    }
}
