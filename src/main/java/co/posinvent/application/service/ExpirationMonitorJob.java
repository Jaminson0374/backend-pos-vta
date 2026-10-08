package co.posinvent.application.service;

import co.posinvent.application.usecase.DisposeExpiredBatchUseCase;
import co.posinvent.domain.repository.StockDisposalRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Scheduled monitor that detects expired and near-expiry batches. Disposal of an
 * expired batch is delegated to {@link DisposeExpiredBatchUseCase}, which owns the
 * transactional unit of work (no self-invocation here).
 */
@Service
public class ExpirationMonitorJob {

    private static final Logger log = LoggerFactory.getLogger(ExpirationMonitorJob.class);

    private final StockDisposalRepository disposalRepo;
    private final DisposeExpiredBatchUseCase disposeExpiredBatch;

    @Value("${app.inventory.auto-dispose:false}")
    private boolean autoDispose;

    @Value("${app.inventory.expiration-warning-days:30}")
    private int warningDays;

    public ExpirationMonitorJob(
            StockDisposalRepository disposalRepo,
            DisposeExpiredBatchUseCase disposeExpiredBatch
    ) {
        this.disposalRepo = disposalRepo;
        this.disposeExpiredBatch = disposeExpiredBatch;
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
                    disposeExpiredBatch.execute(
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
}
