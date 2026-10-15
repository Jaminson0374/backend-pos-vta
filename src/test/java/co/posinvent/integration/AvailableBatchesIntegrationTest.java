package co.posinvent.integration;

import co.posinvent.application.dto.AvailableBatchResponse;
import co.posinvent.application.usecase.StockQueryUseCase;
import co.posinvent.domain.model.Batch;
import co.posinvent.domain.model.InventoryStock;
import co.posinvent.domain.model.Warehouse;
import co.posinvent.domain.repository.StockRepository;
import co.posinvent.domain.repository.ThirdPartyRepository;
import co.posinvent.infrastructure.adapters.out.persistence.BatchEntity;
import co.posinvent.infrastructure.adapters.out.persistence.TestDataFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for the available-batches query (audit A1, WU-10).
 *
 * <p>Seeds several OPEN batches for the same product: two in the target warehouse with
 * different expiration dates, one with no expiration date, one fully committed (available
 * quantity zero) and one in a DIFFERENT warehouse. It then asserts that
 * {@link StockQueryUseCase#getAvailableBatches(UUID, UUID)} returns only the reservable
 * rows of the target product + warehouse, excludes the fully committed row, and orders the
 * result in FEFO order (expiration ascending, rows without expiration last).</p>
 *
 * <p>The class is deliberately NOT {@code @Transactional}: the fixture commits via a
 * {@link TransactionTemplate} so the use case (which opens its own read-only transaction)
 * observes committed rows exactly like a real HTTP caller.</p>
 */
class AvailableBatchesIntegrationTest extends AbstractIntegrationTest {

    @Autowired private StockQueryUseCase stockQueryUseCase;
    @Autowired private StockRepository stockRepo;
    @Autowired private ThirdPartyRepository thirdPartyRepo;

    private UUID warehouseId;
    private UUID otherWarehouseId;
    private UUID productId;

    private UUID batchEarly;
    private UUID batchLate;
    private UUID batchNoExpiry;
    private UUID batchFullyCommitted;
    private UUID batchOtherWarehouse;

    private LocalDate earlyExpiry;
    private LocalDate lateExpiry;

    @BeforeEach
    void setUp() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            warehouseId = TestDataFactory.createWarehouse(
                    em, "AVAIL Test", Warehouse.WarehouseType.CORTES);
            otherWarehouseId = TestDataFactory.createWarehouse(
                    em, "AVAIL Other", Warehouse.WarehouseType.CORTES);
            productId = TestDataFactory.createProduct(
                    em, "TEST-AVB", "Available Batches", false, true);
            em.flush();

            var supplierId = thirdPartyRepo.findByNumIdentification("000000000-0").orElseThrow().id();

            earlyExpiry = LocalDate.now().plusDays(10);
            lateExpiry = LocalDate.now().plusDays(30);

            batchEarly = TestDataFactory.createBatch(
                    em, productId, supplierId, warehouseId, bd(50), Batch.BatchStatus.OPEN);
            batchLate = TestDataFactory.createBatch(
                    em, productId, supplierId, warehouseId, bd(100), Batch.BatchStatus.OPEN);
            batchNoExpiry = TestDataFactory.createBatch(
                    em, productId, supplierId, warehouseId, bd(7), Batch.BatchStatus.OPEN);
            batchFullyCommitted = TestDataFactory.createBatch(
                    em, productId, supplierId, warehouseId, bd(20), Batch.BatchStatus.OPEN);
            batchOtherWarehouse = TestDataFactory.createBatch(
                    em, productId, supplierId, otherWarehouseId, bd(80), Batch.BatchStatus.OPEN);

            // batchNoExpiry intentionally keeps a null expiration_date (NULLS LAST).
            setExpiration(batchEarly, earlyExpiry);
            setExpiration(batchLate, lateExpiry);
            setExpiration(batchFullyCommitted, LocalDate.now().plusDays(1));
            setExpiration(batchOtherWarehouse, LocalDate.now().plusDays(5));

            TestDataFactory.createStock(em, productId, batchEarly, warehouseId, bd(50), BigDecimal.ZERO);
            TestDataFactory.createStock(em, productId, batchLate, warehouseId, bd(100), BigDecimal.ZERO);
            TestDataFactory.createStock(em, productId, batchNoExpiry, warehouseId, bd(7), BigDecimal.ZERO);
            TestDataFactory.createStock(em, productId, batchFullyCommitted, warehouseId, bd(20), BigDecimal.ZERO);
            TestDataFactory.createStock(em, productId, batchOtherWarehouse, otherWarehouseId, bd(80), BigDecimal.ZERO);
            em.flush();

            // Fully commit this row: available = current - committed = 20 - 20 = 0 -> excluded.
            commitAll(batchFullyCommitted, warehouseId, bd(20));
            em.flush();
        });
    }

    @Test
    void returnsOnlyReservableBatchesForProductAndWarehouseInFefoOrder() {
        var result = stockQueryUseCase.getAvailableBatches(productId, warehouseId);

        // Only the target product + warehouse rows, ordered FEFO with null expiry last.
        assertThat(result).extracting(AvailableBatchResponse::batchId)
                .containsExactly(batchEarly, batchLate, batchNoExpiry);

        // The fully committed row and the other warehouse row must be excluded.
        assertThat(result).extracting(AvailableBatchResponse::batchId)
                .doesNotContain(batchFullyCommitted, batchOtherWarehouse);

        // Every returned row must carry a strictly positive available quantity.
        assertThat(result).allSatisfy(r ->
                assertThat(r.availableQuantity()).isGreaterThan(BigDecimal.ZERO));

        assertThat(result.get(0).batchId()).isEqualTo(batchEarly);
        assertThat(result.get(0).expirationDate()).isEqualTo(earlyExpiry);
        assertThat(result.get(0).availableQuantity()).isEqualByComparingTo(bd(50));

        assertThat(result.get(1).batchId()).isEqualTo(batchLate);
        assertThat(result.get(1).expirationDate()).isEqualTo(lateExpiry);
        assertThat(result.get(1).availableQuantity()).isEqualByComparingTo(bd(100));

        assertThat(result.get(2).batchId()).isEqualTo(batchNoExpiry);
        assertThat(result.get(2).expirationDate()).isNull();
        assertThat(result.get(2).availableQuantity()).isEqualByComparingTo(bd(7));
    }

    @Test
    void returnsEmptyListWhenWarehouseHasNoReservableStock() {
        var result = stockQueryUseCase.getAvailableBatches(productId, UUID.randomUUID());

        assertThat(result).isEmpty();
    }

    @Test
    void doesNotLeakOtherWarehouseBatchesEvenWhenReservable() {
        var result = stockQueryUseCase.getAvailableBatches(productId, otherWarehouseId);

        assertThat(result).extracting(AvailableBatchResponse::batchId)
                .containsExactly(batchOtherWarehouse);
    }

    private void setExpiration(UUID batchId, LocalDate expiration) {
        em.find(BatchEntity.class, batchId).setExpirationDate(expiration);
    }

    private void commitAll(UUID batchId, UUID warehouseId, BigDecimal committed) {
        var seeded = stockRepo.findByProductBatchWarehouse(productId, batchId, warehouseId).orElseThrow();
        stockRepo.save(new InventoryStock(
                seeded.id(), seeded.productId(), seeded.batchId(), seeded.warehouseId(),
                seeded.currentQuantity(), committed,
                seeded.unitCost(), seeded.createdAt(), seeded.updatedAt()));
    }

    private static BigDecimal bd(long value) {
        return BigDecimal.valueOf(value);
    }
}
