package co.posinvent.application.service;

import co.posinvent.application.usecase.RecordMovementUseCase;
import co.posinvent.domain.model.Batch;
import co.posinvent.domain.model.Batch.BatchStatus;
import co.posinvent.domain.model.InventoryStock;
import co.posinvent.domain.model.MovementType;
import co.posinvent.domain.repository.BatchRepository;
import co.posinvent.domain.repository.StockDisposalRepository;
import co.posinvent.domain.repository.StockRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
public class ExpirationMonitorJob {

    private static final Logger log = LoggerFactory.getLogger(ExpirationMonitorJob.class);

    private final StockDisposalRepository disposalRepo;
    private final StockRepository stockRepository;
    private final BatchRepository batchRepository;
    private final RecordMovementUseCase recordMovement;

    @Value("${app.inventory.auto-dispose:false}")
    private boolean autoDispose;

    @Value("${app.inventory.expiration-warning-days:30}")
    private int warningDays;

    public ExpirationMonitorJob(
            StockDisposalRepository disposalRepo,
            StockRepository stockRepository,
            BatchRepository batchRepository,
            RecordMovementUseCase recordMovement
    ) {
        this.disposalRepo = disposalRepo;
        this.stockRepository = stockRepository;
        this.batchRepository = batchRepository;
        this.recordMovement = recordMovement;
    }

    @Scheduled(cron = "0 0 6 * * *")
    public void checkExpiringBatches() {
        // Window 1: Already expired — auto-dispose if enabled
        var expired = disposalRepo.findExpiredBatches();
        if (!expired.isEmpty()) {
            log.warn("Hay {} lotes VENCIDOS:", expired.size());
            for (var batch : expired) {
                log.info("  Lote {} — {} — {} — vence: {} — stock: {}",
                        batch.get("batch_id"), batch.get("product_name"),
                        batch.get("warehouse_name"), batch.get("expiration_date"),
                        batch.get("current_qty"));

                if (autoDispose && batch.get("current_qty") instanceof Number qty && qty.doubleValue() > 0) {
                    disposeExpiredBatch(
                            (UUID) batch.get("batch_id"),
                            (UUID) batch.get("product_id"),
                            (UUID) batch.get("warehouse_id"),
                            new BigDecimal(qty.toString())
                    );
                }
            }
        }

        // Window 2: Expiring soon — always just warn
        if (warningDays > 0) {
            var expiring = disposalRepo.findExpiringBatches(warningDays);
            if (!expiring.isEmpty()) {
                log.warn("Hay {} lotes próximos a vencer ({} días):", expiring.size(), warningDays);
                for (var batch : expiring) {
                    log.info("  Lote {} — producto {} — bodega {} — vence: {}",
                            batch.get("batch_id"), batch.get("product_name"),
                            batch.get("warehouse_name"), batch.get("expiration_date"));
                }
            }
        }
    }

    @Transactional
    void disposeExpiredBatch(UUID batchId, UUID productId, UUID warehouseId, BigDecimal remainingQty) {
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
