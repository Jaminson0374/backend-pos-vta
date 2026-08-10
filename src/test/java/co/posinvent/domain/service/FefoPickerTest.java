package co.posinvent.domain.service;

import co.posinvent.domain.exception.BusinessException;
import co.posinvent.domain.model.BatchAllocation;
import co.posinvent.domain.model.InventoryStock;
import co.posinvent.domain.repository.StockRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FefoPickerTest {

    @Mock
    private StockRepository stockRepository;

    private FefoPicker fefoPicker;

    private static final UUID PRODUCT_ID = UUID.randomUUID();
    private static final UUID WAREHOUSE_ID = UUID.randomUUID();
    private static final UUID BATCH1_ID = UUID.randomUUID();
    private static final UUID BATCH2_ID = UUID.randomUUID();
    private static final UUID BATCH3_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        fefoPicker = new FefoPicker(stockRepository);
    }

    // ── Scenario 1: Stock insufficient ──────────────────────────────────

    @Test
    void shouldThrowInsufficientStockWhenNotEnoughAvailable() {
        var stock = inventoryStock(BATCH1_ID, new BigDecimal("5"));
        when(stockRepository.findAvailableByProductWarehouse(PRODUCT_ID, WAREHOUSE_ID))
                .thenReturn(List.of(stock));

        assertThatThrownBy(() -> fefoPicker.pick(PRODUCT_ID, WAREHOUSE_ID, new BigDecimal("10")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", "INSUFFICIENT_STOCK");
    }

    // ── Scenario 2: No stock at all ─────────────────────────────────────

    @Test
    void shouldThrowNoStockAvailableWhenEmptyList() {
        when(stockRepository.findAvailableByProductWarehouse(PRODUCT_ID, WAREHOUSE_ID))
                .thenReturn(List.of());

        assertThatThrownBy(() -> fefoPicker.pick(PRODUCT_ID, WAREHOUSE_ID, BigDecimal.ONE))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", "NO_STOCK_AVAILABLE");
    }

    // ── Scenario 3: Single batch, exact quantity ────────────────────────

    @Test
    void shouldReturnOneAllocationWhenExactQtyAvailableInSingleBatch() {
        var stock = inventoryStock(BATCH1_ID, new BigDecimal("10"));
        when(stockRepository.findAvailableByProductWarehouse(PRODUCT_ID, WAREHOUSE_ID))
                .thenReturn(List.of(stock));

        List<BatchAllocation> allocations = fefoPicker.pick(PRODUCT_ID, WAREHOUSE_ID, new BigDecimal("10"));

        assertThat(allocations).hasSize(1);
        assertThat(allocations.get(0).batchId()).isEqualTo(BATCH1_ID);
        assertThat(allocations.get(0).quantity()).isEqualByComparingTo("10");
        assertThat(allocations.get(0).unitCost()).isEqualByComparingTo("100.000000");
    }

    // ── Scenario 4: Two batches, partial consumption ────────────────────

    @Test
    void shouldPickFromTwoBatchesWhenFirstIsInsufficient() {
        var stock1 = inventoryStock(BATCH1_ID, new BigDecimal("3"));
        var stock2 = inventoryStock(BATCH2_ID, new BigDecimal("10"));
        when(stockRepository.findAvailableByProductWarehouse(PRODUCT_ID, WAREHOUSE_ID))
                .thenReturn(List.of(stock1, stock2));

        List<BatchAllocation> allocations = fefoPicker.pick(PRODUCT_ID, WAREHOUSE_ID, new BigDecimal("8"));

        assertThat(allocations).hasSize(2);
        // First batch: picks all 3
        assertThat(allocations.get(0).batchId()).isEqualTo(BATCH1_ID);
        assertThat(allocations.get(0).quantity()).isEqualByComparingTo("3");
        // Second batch: picks remaining 5
        assertThat(allocations.get(1).batchId()).isEqualTo(BATCH2_ID);
        assertThat(allocations.get(1).quantity()).isEqualByComparingTo("5");
    }

    // ── Scenario 5: Batch without expirationDate — still picked, ordered
    //    after entries with dates (ordering is repository responsibility) ─

    @Test
    void shouldPickFromBatchWithoutExpirationDateWhenOrderedAfterDatedBatches() {
        // Repository returns dated batch first, then null-expiration batch.
        // FefoPicker consumes in that order.
        var datedStock = inventoryStock(BATCH1_ID, new BigDecimal("2"));
        var noDateStock = inventoryStock(BATCH2_ID, new BigDecimal("5"));
        when(stockRepository.findAvailableByProductWarehouse(PRODUCT_ID, WAREHOUSE_ID))
                .thenReturn(List.of(datedStock, noDateStock));

        List<BatchAllocation> allocations = fefoPicker.pick(PRODUCT_ID, WAREHOUSE_ID, new BigDecimal("6"));

        assertThat(allocations).hasSize(2);
        assertThat(allocations.get(0).batchId()).isEqualTo(BATCH1_ID);
        assertThat(allocations.get(0).quantity()).isEqualByComparingTo("2");
        assertThat(allocations.get(1).batchId()).isEqualTo(BATCH2_ID);
        assertThat(allocations.get(1).quantity()).isEqualByComparingTo("4");
    }

    // ── Scenario 6: Multiple batches, some with null expirationDate —
    //    nulls go last (repository ordering), only consumed if needed ─────

    @Test
    void shouldConsumeNullExpirationBatchesLastOnlyIfNeeded() {
        var dated1 = inventoryStock(BATCH1_ID, new BigDecimal("3"));
        var dated2 = inventoryStock(BATCH2_ID, new BigDecimal("4"));
        var nullDate = inventoryStock(BATCH3_ID, new BigDecimal("5"));
        // Repository already orders nulls last
        when(stockRepository.findAvailableByProductWarehouse(PRODUCT_ID, WAREHOUSE_ID))
                .thenReturn(List.of(dated1, dated2, nullDate));

        List<BatchAllocation> allocations = fefoPicker.pick(PRODUCT_ID, WAREHOUSE_ID, new BigDecimal("5"));

        // Should consume dated1 (3) + dated2 (2), nullDate not touched
        assertThat(allocations).hasSize(2);
        assertThat(allocations.get(0).batchId()).isEqualTo(BATCH1_ID);
        assertThat(allocations.get(0).quantity()).isEqualByComparingTo("3");
        assertThat(allocations.get(1).batchId()).isEqualTo(BATCH2_ID);
        assertThat(allocations.get(1).quantity()).isEqualByComparingTo("2");
    }

    // ── Helpers ─────────────────────────────────────────────────────────

    private InventoryStock inventoryStock(UUID batchId, BigDecimal availableQty) {
        return new InventoryStock(
                UUID.randomUUID(),
                PRODUCT_ID,
                batchId,
                WAREHOUSE_ID,
                availableQty,           // currentQuantity = availableQty
                BigDecimal.ZERO,        // committedQuantity = 0, so available = current - 0
                new BigDecimal("100.000000"),
                OffsetDateTime.now().minusDays(1),
                OffsetDateTime.now()
        );
    }
}
