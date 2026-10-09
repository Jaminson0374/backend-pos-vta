package co.posinvent.application.usecase;

import co.posinvent.application.dto.ManualStockEntryRequest;
import co.posinvent.domain.exception.ResourceNotFoundException;
import co.posinvent.domain.model.InventoryStock;
import co.posinvent.domain.model.MovementType;
import co.posinvent.domain.model.Product;
import co.posinvent.domain.repository.ProductRepository;
import co.posinvent.domain.repository.StockRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ManualStockEntryUseCaseTest {

    @Mock
    private StockRepository stockRepo;

    @Mock
    private ProductRepository productRepo;

    @Mock
    private RecordMovementUseCase recordMovement;

    @Mock
    private OptimisticConcurrencyExecutor concurrencyExecutor;

    private ManualStockEntryUseCase useCase;

    private static final UUID PRODUCT_ID = UUID.randomUUID();
    private static final UUID BATCH_ID = UUID.randomUUID();
    private static final UUID WAREHOUSE_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        useCase = new ManualStockEntryUseCase(stockRepo, productRepo, recordMovement, concurrencyExecutor);
        doAnswer(inv -> ((Supplier<?>) inv.getArgument(0)).get())
                .when(concurrencyExecutor).execute(any());
    }

    @Test
    void execute_createsNewStockWhenNoneExists() {
        when(productRepo.findById(PRODUCT_ID)).thenReturn(Optional.of(product()));
        when(stockRepo.findByProductBatchWarehouse(PRODUCT_ID, BATCH_ID, WAREHOUSE_ID))
                .thenReturn(Optional.empty());

        useCase.execute(new ManualStockEntryRequest(
                PRODUCT_ID, BATCH_ID, WAREHOUSE_ID,
                new BigDecimal("10"), new BigDecimal("5"), "Entrada manual"));

        var stockCaptor = ArgumentCaptor.forClass(InventoryStock.class);
        verify(stockRepo).save(stockCaptor.capture());
        var saved = stockCaptor.getValue();
        assertThat(saved.id()).isNull();
        assertThat(saved.currentQuantity()).isEqualByComparingTo("10");
        assertThat(saved.unitCost()).isEqualByComparingTo("5");

        verify(recordMovement).record(
                eq(PRODUCT_ID), eq(BATCH_ID), eq(WAREHOUSE_ID), eq(MovementType.ENTRY),
                eq(new BigDecimal("10")), eq(new BigDecimal("5")),
                eq(BigDecimal.ZERO), eq(new BigDecimal("10")),
                eq("MANUAL_ENTRY"), isNull(), eq("Entrada manual"));

        verify(productRepo).recalculateTotalStock(PRODUCT_ID);
    }

    @Test
    void execute_sumsQuantityIntoExistingStock() {
        when(productRepo.findById(PRODUCT_ID)).thenReturn(Optional.of(product()));
        when(stockRepo.findByProductBatchWarehouse(PRODUCT_ID, BATCH_ID, WAREHOUSE_ID))
                .thenReturn(Optional.of(stock("4", "5")));

        useCase.execute(new ManualStockEntryRequest(
                PRODUCT_ID, BATCH_ID, WAREHOUSE_ID,
                new BigDecimal("6"), null, "Reposición"));

        var stockCaptor = ArgumentCaptor.forClass(InventoryStock.class);
        verify(stockRepo).save(stockCaptor.capture());
        var saved = stockCaptor.getValue();
        assertThat(saved.currentQuantity()).isEqualByComparingTo("10");
        // null unitCost keeps the previous stock cost.
        assertThat(saved.unitCost()).isEqualByComparingTo("5");

        verify(recordMovement).record(
                eq(PRODUCT_ID), eq(BATCH_ID), eq(WAREHOUSE_ID), eq(MovementType.ENTRY),
                eq(new BigDecimal("6")), eq(BigDecimal.ZERO),
                eq(new BigDecimal("4")), eq(new BigDecimal("10")),
                eq("MANUAL_ENTRY"), isNull(), eq("Reposición"));

        verify(productRepo).recalculateTotalStock(PRODUCT_ID);
    }

    @Test
    void execute_throwsWhenProductDoesNotExist() {
        when(productRepo.findById(PRODUCT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(new ManualStockEntryRequest(
                PRODUCT_ID, BATCH_ID, WAREHOUSE_ID, new BigDecimal("10"), BigDecimal.ONE, null)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Producto");

        verify(stockRepo, never()).save(any());
        verify(recordMovement, never()).record(any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any());
    }

    private InventoryStock stock(String quantity, String unitCost) {
        return new InventoryStock(
                UUID.randomUUID(), PRODUCT_ID, BATCH_ID, WAREHOUSE_ID,
                new BigDecimal(quantity), BigDecimal.ZERO, new BigDecimal(unitCost),
                OffsetDateTime.now().minusDays(1), OffsetDateTime.now().minusHours(1));
    }

    private Product product() {
        return new Product(
                PRODUCT_ID, "P-" + PRODUCT_ID, "Producto",
                null, null, null, null, null, null, null, null, null, null,
                BigDecimal.ONE, BigDecimal.ZERO, "EXENTO", BigDecimal.TEN,
                "PROMEDIO_PONDERADO",
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.TEN, BigDecimal.ZERO,
                false, false, false, false, false, false, true,
                null, null, null, null, null, null, null,
                true, 0,
                OffsetDateTime.now().minusDays(2), OffsetDateTime.now().minusDays(1),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
    }
}
