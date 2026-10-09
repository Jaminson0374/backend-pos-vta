package co.posinvent.application.usecase;

import co.posinvent.application.dto.ManualStockExitRequest;
import co.posinvent.domain.exception.BusinessException;
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
class ManualStockExitUseCaseTest {

    @Mock
    private StockRepository stockRepo;

    @Mock
    private ProductRepository productRepo;

    @Mock
    private RecordMovementUseCase recordMovement;

    @Mock
    private OptimisticConcurrencyExecutor concurrencyExecutor;

    private ManualStockExitUseCase useCase;

    private static final UUID PRODUCT_ID = UUID.randomUUID();
    private static final UUID BATCH_ID = UUID.randomUUID();
    private static final UUID WAREHOUSE_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        useCase = new ManualStockExitUseCase(stockRepo, productRepo, recordMovement, concurrencyExecutor);
        doAnswer(inv -> ((Supplier<?>) inv.getArgument(0)).get())
                .when(concurrencyExecutor).execute(any());
    }

    @Test
    void execute_decrementsStockAndRecordsExitMovement() {
        when(productRepo.findById(PRODUCT_ID)).thenReturn(Optional.of(product()));
        when(stockRepo.findByProductBatchWarehouse(PRODUCT_ID, BATCH_ID, WAREHOUSE_ID))
                .thenReturn(Optional.of(stock("10", "5")));

        useCase.execute(new ManualStockExitRequest(
                PRODUCT_ID, BATCH_ID, WAREHOUSE_ID, new BigDecimal("4"), "Consumo interno"));

        var stockCaptor = ArgumentCaptor.forClass(InventoryStock.class);
        verify(stockRepo).save(stockCaptor.capture());
        assertThat(stockCaptor.getValue().currentQuantity()).isEqualByComparingTo("6");

        verify(recordMovement).record(
                eq(PRODUCT_ID), eq(BATCH_ID), eq(WAREHOUSE_ID), eq(MovementType.EXIT),
                eq(new BigDecimal("4")), eq(new BigDecimal("5")),
                eq(new BigDecimal("10")), eq(new BigDecimal("6")),
                eq("MANUAL_EXIT"), isNull(), eq("Consumo interno"));

        verify(productRepo).recalculateTotalStock(PRODUCT_ID);
    }

    @Test
    void execute_throwsWhenNoStockExists() {
        when(productRepo.findById(PRODUCT_ID)).thenReturn(Optional.of(product()));
        when(stockRepo.findByProductBatchWarehouse(PRODUCT_ID, BATCH_ID, WAREHOUSE_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(new ManualStockExitRequest(
                PRODUCT_ID, BATCH_ID, WAREHOUSE_ID, new BigDecimal("1"), null)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", "STOCK_NOT_FOUND");

        verify(stockRepo, never()).save(any());
    }

    @Test
    void execute_throwsWhenQuantityExceedsAvailableStock() {
        when(productRepo.findById(PRODUCT_ID)).thenReturn(Optional.of(product()));
        when(stockRepo.findByProductBatchWarehouse(PRODUCT_ID, BATCH_ID, WAREHOUSE_ID))
                .thenReturn(Optional.of(stock("3", "5")));

        assertThatThrownBy(() -> useCase.execute(new ManualStockExitRequest(
                PRODUCT_ID, BATCH_ID, WAREHOUSE_ID, new BigDecimal("4"), null)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", "INSUFFICIENT_STOCK");

        verify(stockRepo, never()).save(any());
        verify(recordMovement, never()).record(any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any());
    }

    @Test
    void execute_throwsWhenProductDoesNotExist() {
        when(productRepo.findById(PRODUCT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(new ManualStockExitRequest(
                PRODUCT_ID, BATCH_ID, WAREHOUSE_ID, BigDecimal.ONE, null)))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(stockRepo, never()).save(any());
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
