package co.posinvent.application.usecase;

import co.posinvent.application.dto.AdjustmentRequest;
import co.posinvent.domain.exception.BusinessException;
import co.posinvent.domain.exception.ResourceNotFoundException;
import co.posinvent.domain.model.InventoryStock;
import co.posinvent.domain.model.MovementType;
import co.posinvent.domain.model.Product;
import co.posinvent.domain.model.StockAdjustment;
import co.posinvent.domain.repository.ProductRepository;
import co.posinvent.domain.repository.StockAdjustmentRepository;
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
class CreateAdjustmentUseCaseTest {

    @Mock
    private StockAdjustmentRepository adjustmentRepo;

    @Mock
    private StockRepository stockRepo;

    @Mock
    private ProductRepository productRepo;

    @Mock
    private RecordMovementUseCase recordMovement;

    @Mock
    private OptimisticConcurrencyExecutor concurrencyExecutor;

    private CreateAdjustmentUseCase useCase;

    private static final UUID PRODUCT_ID = UUID.randomUUID();
    private static final UUID BATCH_ID = UUID.randomUUID();
    private static final UUID WAREHOUSE_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        useCase = new CreateAdjustmentUseCase(
                adjustmentRepo, stockRepo, productRepo, recordMovement, concurrencyExecutor);
        doAnswer(inv -> ((Supplier<?>) inv.getArgument(0)).get())
                .when(concurrencyExecutor).execute(any());
    }

    @Test
    void execute_setsQuantityAfterOnExistingStockAndRecordsSignedDelta() {
        stubProductExists();
        stubAdjustmentSave();
        when(stockRepo.findByProductBatchWarehouse(PRODUCT_ID, BATCH_ID, WAREHOUSE_ID))
                .thenReturn(Optional.of(stock("10", "5")));

        var response = useCase.execute(new AdjustmentRequest(
                PRODUCT_ID, BATCH_ID, WAREHOUSE_ID,
                "PHYSICAL_COUNT", new BigDecimal("15"), "Conteo físico"));

        var stockCaptor = ArgumentCaptor.forClass(InventoryStock.class);
        verify(stockRepo).save(stockCaptor.capture());
        assertThat(stockCaptor.getValue().currentQuantity()).isEqualByComparingTo("15");

        // Signed delta = 15 - 10 = +5
        var deltaCaptor = ArgumentCaptor.forClass(BigDecimal.class);
        verify(recordMovement).record(
                eq(PRODUCT_ID), eq(BATCH_ID), eq(WAREHOUSE_ID), eq(MovementType.ADJUSTMENT),
                deltaCaptor.capture(), eq(new BigDecimal("5")),
                eq(new BigDecimal("10")), eq(new BigDecimal("15")),
                eq("ADJUSTMENT"), any(), eq("PHYSICAL_COUNT: Conteo físico"));
        assertThat(deltaCaptor.getValue()).isEqualByComparingTo("5");

        assertThat(response.adjustmentType()).isEqualTo("PHYSICAL_COUNT");
        assertThat(response.quantityBefore()).isEqualByComparingTo("10");
        assertThat(response.quantityAfter()).isEqualByComparingTo("15");
        assertThat(response.id()).isNotNull();

        verify(productRepo).recalculateTotalStock(PRODUCT_ID);
    }

    @Test
    void execute_recordsNegativeDeltaWhenReducingStock() {
        stubProductExists();
        stubAdjustmentSave();
        when(stockRepo.findByProductBatchWarehouse(PRODUCT_ID, BATCH_ID, WAREHOUSE_ID))
                .thenReturn(Optional.of(stock("10", "5")));

        useCase.execute(new AdjustmentRequest(
                PRODUCT_ID, BATCH_ID, WAREHOUSE_ID,
                "DAMAGE", new BigDecimal("3"), "Merma"));

        var deltaCaptor = ArgumentCaptor.forClass(BigDecimal.class);
        verify(recordMovement).record(
                any(), any(), any(), eq(MovementType.ADJUSTMENT),
                deltaCaptor.capture(), any(), any(), any(), any(), any(), any());
        assertThat(deltaCaptor.getValue()).isEqualByComparingTo("-7");
    }

    @Test
    void execute_createsStockWhenNoneExistsAndQuantityAfterIsPositive() {
        stubProductExists();
        stubAdjustmentSave();
        when(stockRepo.findByProductBatchWarehouse(PRODUCT_ID, BATCH_ID, WAREHOUSE_ID))
                .thenReturn(Optional.empty());

        var response = useCase.execute(new AdjustmentRequest(
                PRODUCT_ID, BATCH_ID, WAREHOUSE_ID,
                "OTHER", new BigDecimal("8"), "Alta por ajuste"));

        var stockCaptor = ArgumentCaptor.forClass(InventoryStock.class);
        verify(stockRepo).save(stockCaptor.capture());
        assertThat(stockCaptor.getValue().id()).isNull();
        assertThat(stockCaptor.getValue().currentQuantity()).isEqualByComparingTo("8");

        assertThat(response.quantityBefore()).isEqualByComparingTo("0");
        assertThat(response.quantityAfter()).isEqualByComparingTo("8");
    }

    @Test
    void execute_rejectsAdjustmentToZeroWhenNoStockExists() {
        stubProductExists();
        when(stockRepo.findByProductBatchWarehouse(PRODUCT_ID, BATCH_ID, WAREHOUSE_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(new AdjustmentRequest(
                PRODUCT_ID, BATCH_ID, WAREHOUSE_ID,
                "PHYSICAL_COUNT", BigDecimal.ZERO, "Conteo")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", "ADJ_NO_STOCK");

        verify(stockRepo, never()).save(any());
        verify(adjustmentRepo, never()).save(any());
    }

    @Test
    void execute_rejectsAdjustmentToNegativeWhenNoStockExists() {
        stubProductExists();
        when(stockRepo.findByProductBatchWarehouse(PRODUCT_ID, BATCH_ID, WAREHOUSE_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(new AdjustmentRequest(
                PRODUCT_ID, BATCH_ID, WAREHOUSE_ID,
                "THEFT", new BigDecimal("-2"), "Faltante")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", "ADJ_NO_STOCK");
    }

    @Test
    void execute_throwsWhenProductDoesNotExist() {
        when(productRepo.findById(PRODUCT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(new AdjustmentRequest(
                PRODUCT_ID, BATCH_ID, WAREHOUSE_ID,
                "PHYSICAL_COUNT", BigDecimal.TEN, "Conteo")))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    private void stubProductExists() {
        when(productRepo.findById(PRODUCT_ID)).thenReturn(Optional.of(product()));
    }

    private void stubAdjustmentSave() {
        doAnswer(inv -> withId(inv.getArgument(0))).when(adjustmentRepo).save(any(StockAdjustment.class));
    }

    private StockAdjustment withId(StockAdjustment a) {
        return new StockAdjustment(
                UUID.randomUUID(), a.productId(), a.batchId(), a.warehouseId(),
                a.adjustmentType(), a.quantityBefore(), a.quantityAfter(),
                a.unitCost(), a.reason(), a.createdBy(), OffsetDateTime.now());
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
