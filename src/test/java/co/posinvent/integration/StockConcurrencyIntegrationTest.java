package co.posinvent.integration;

import co.posinvent.application.dto.ManualStockEntryRequest;
import co.posinvent.application.dto.ManualStockExitRequest;
import co.posinvent.application.usecase.ManualStockEntryUseCase;
import co.posinvent.application.usecase.ManualStockExitUseCase;
import co.posinvent.domain.exception.BusinessException;
import co.posinvent.domain.model.Batch;
import co.posinvent.domain.model.Warehouse;
import co.posinvent.domain.repository.StockRepository;
import co.posinvent.domain.repository.ThirdPartyRepository;
import co.posinvent.infrastructure.adapters.out.persistence.InventoryStockEntity;
import co.posinvent.infrastructure.adapters.out.persistence.TestDataFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for stock concurrency hardening (REQ-CONC-002 / REQ-CONC-004).
 *
 * <p>Runs against Testcontainers PostgreSQL 16. Setup data is committed via a
 * {@link TransactionTemplate} (the test class is deliberately NOT {@code @Transactional})
 * so the concurrent worker threads can observe and mutate the committed row in their
 * own REQUIRES_NEW transactions, exactly like production callers.</p>
 */
class StockConcurrencyIntegrationTest extends AbstractIntegrationTest {

    @Autowired private ManualStockExitUseCase exitUseCase;
    @Autowired private ManualStockEntryUseCase entryUseCase;
    @Autowired private StockRepository stockRepo;
    @Autowired private ThirdPartyRepository thirdPartyRepo;
    @Autowired private PlatformTransactionManager transactionManager;

    private UUID warehouseId;
    private UUID productId;
    private UUID batchId;
    private UUID stockId;

    @BeforeEach
    void setUp() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            warehouseId = TestDataFactory.createWarehouse(
                    em, "CONCURRENCY Test", Warehouse.WarehouseType.CORTES);
            productId = TestDataFactory.createProduct(
                    em, "TEST-CONC", "Stock Concurrency", false, true);
            em.flush();

            var systemSupplier = thirdPartyRepo.findByNumIdentification("000000000-0").orElseThrow();
            batchId = TestDataFactory.createBatch(
                    em, productId, systemSupplier.id(), warehouseId, new BigDecimal("100"), Batch.BatchStatus.OPEN);
            TestDataFactory.createStock(
                    em, productId, batchId, warehouseId, new BigDecimal("100"), BigDecimal.ZERO);
            em.flush();
        });

        stockId = stockRepo.findByProductBatchWarehouse(productId, batchId, warehouseId)
                .orElseThrow().id();
    }

    @Test
    void oneHundredConcurrentDecrementsConvergeToZeroWithoutLostUpdates() throws Exception {
        int workers = 100;
        ExecutorService pool = Executors.newFixedThreadPool(workers);
        CountDownLatch startGate = new CountDownLatch(1);
        List<Throwable> failures = Collections.synchronizedList(new ArrayList<>());
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < workers; i++) {
            futures.add(pool.submit(() -> {
                try {
                    startGate.await();
                    exitUseCase.execute(new ManualStockExitRequest(
                            productId, batchId, warehouseId, BigDecimal.ONE, "concurrency-decrement"));
                } catch (Throwable t) {
                    failures.add(t);
                }
                return null;
            }));
        }

        startGate.countDown();

        for (Future<?> future : futures) {
            future.get(2, TimeUnit.MINUTES);
        }
        pool.shutdown();

        assertThat(failures).isEmpty();

        var stock = stockRepo.findByProductBatchWarehouse(productId, batchId, warehouseId).orElseThrow();
        assertThat(stock.currentQuantity()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void concurrentIncrementsConvergeWithVersionBumpAndNoConcurrentModification() throws Exception {
        int workers = 2;
        ExecutorService pool = Executors.newFixedThreadPool(workers);
        CountDownLatch startGate = new CountDownLatch(1);
        List<Throwable> failures = Collections.synchronizedList(new ArrayList<>());
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < workers; i++) {
            futures.add(pool.submit(() -> {
                try {
                    startGate.await();
                    entryUseCase.execute(new ManualStockEntryRequest(
                            productId, batchId, warehouseId, BigDecimal.ONE,
                            BigDecimal.ZERO, "concurrency-increment"));
                } catch (Throwable t) {
                    failures.add(t);
                }
                return null;
            }));
        }

        startGate.countDown();

        for (Future<?> future : futures) {
            future.get(2, TimeUnit.MINUTES);
        }
        pool.shutdown();

        assertThat(failures).isEmpty();

        var stock = stockRepo.findByProductBatchWarehouse(productId, batchId, warehouseId).orElseThrow();
        assertThat(stock.currentQuantity()).isEqualByComparingTo(new BigDecimal("102"));

        assertThat(readVersion()).isEqualTo(2L);
    }

    @Test
    void oversellIsRejectedAndQuantityRemainsUnchanged() {
        exitUseCase.execute(new ManualStockExitRequest(
                productId, batchId, warehouseId, new BigDecimal("95"), "reduce-to-five"));

        assertThatThrownBy(() -> exitUseCase.execute(new ManualStockExitRequest(
                productId, batchId, warehouseId, new BigDecimal("8"), "oversell-attempt")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", "INSUFFICIENT_STOCK");

        var stock = stockRepo.findByProductBatchWarehouse(productId, batchId, warehouseId).orElseThrow();
        assertThat(stock.currentQuantity()).isEqualByComparingTo(new BigDecimal("5"));
    }

    private Long readVersion() {
        return new TransactionTemplate(transactionManager).execute(status ->
                em.find(InventoryStockEntity.class, stockId).getVersion());
    }
}
