package co.posinvent.application.usecase;

import co.posinvent.application.dto.DisposalRequest;
import co.posinvent.domain.exception.BusinessException;
import co.posinvent.domain.exception.ResourceNotFoundException;
import co.posinvent.domain.model.DisposalType;
import co.posinvent.domain.model.InventoryStock;
import co.posinvent.domain.model.MovementType;
import co.posinvent.domain.model.Product;
import co.posinvent.domain.model.StockDisposal;
import co.posinvent.domain.repository.ProductRepository;
import co.posinvent.domain.repository.StockDisposalRepository;
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
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreateDisposalUseCaseTest {

    @Mock
    private StockDisposalRepository disposalRepo;

    @Mock
    private StockRepository stockRepo;

    @Mock
    private ProductRepository productRepo;

    @Mock
    private RecordMovementUseCase recordMovement;

    @Mock
    private OptimisticConcurrencyExecutor concurrencyExecutor;

    private CreateDisposalUseCase useCase;

    private static final UUID PRODUCT_ID = UUID.randomUUID();
    private static final UUID BATCH_ID = UUID.randomUUID();
    private static final UUID WAREHOUSE_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        useCase = new CreateDisposalUseCase(
                disposalRepo, stockRepo, productRepo, recordMovement, concurrencyExecutor);
        doAnswer(inv -> ((Supplier<?>) inv.getArgument(0)).get())
                .when(concurrencyExecutor).execute(any());
    }

    @Test
    void execute_decrementsStockRecordsDisposalAndRecalculates() {
        stubProductExists();
        when(stockRepo.findByProductBatchWarehouse(PRODUCT_ID, BATCH_ID, WAREHOUSE_ID))
                .thenReturn(Optional.of(stock("10", "5")));
        doAnswer(inv -> inv.getArgument(0)).when(stockRepo).save(any(InventoryStock.class));
        doAnswer(inv -> withId(inv.getArgument(0))).when(disposalRepo).save(any(StockDisposal.class));

        var response = useCase.execute(new DisposalRequest(
                PRODUCT_ID, BATCH_ID, WAREHOUSE_ID, "SANITARIO", new BigDecimal("4"), "Decomiso sanitario"));

        var stockCaptor = ArgumentCaptor.forClass(InventoryStock.class);
        verify(stockRepo).save(stockCaptor.capture());
        assertThat(stockCaptor.getValue().currentQuantity()).isEqualByComparingTo("6");

        verify(recordMovement).record(
                eq(PRODUCT_ID), eq(BATCH_ID), eq(WAREHOUSE_ID), eq(MovementType.DISPOSAL),
                eq(new BigDecimal("4")), eq(new BigDecimal("5")),
                eq(new BigDecimal("10")), eq(new BigDecimal("6")),
                eq("DISPOSAL"), any(), eq("Decomiso sanitario"));

        assertThat(response.disposalType()).isEqualTo(DisposalType.SANITARIO.name());
        assertThat(response.quantity()).isEqualByComparingTo("4");
        assertThat(response.id()).isNotNull();

        verify(productRepo).recalculateTotalStock(PRODUCT_ID);
    }

    @Test
    void execute_rejectsWhenNoStockExists() {
        stubProductExists();
        when(stockRepo.findByProductBatchWarehouse(PRODUCT_ID, BATCH_ID, WAREHOUSE_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(new DisposalRequest(
                PRODUCT_ID, BATCH_ID, WAREHOUSE_ID, "SANITARIO", BigDecimal.ONE, null)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", "NO_STOCK");

        verify(stockRepo, never()).save(any());
        verify(disposalRepo, never()).save(any());
    }

    @Test
    void execute_rejectsWhenQuantityExceedsAvailableStock() {
        stubProductExists();
        when(stockRepo.findByProductBatchWarehouse(PRODUCT_ID, BATCH_ID, WAREHOUSE_ID))
                .thenReturn(Optional.of(stock("3", "5")));

        assertThatThrownBy(() -> useCase.execute(new DisposalRequest(
                PRODUCT_ID, BATCH_ID, WAREHOUSE_ID, "MERMA_PROCESO", new BigDecimal("4"), null)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", "INSUFFICIENT_STOCK");

        verify(stockRepo, never()).save(any());
    }

    @Test
    void execute_throwsWhenProductDoesNotExist() {
        when(productRepo.findById(PRODUCT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(new DisposalRequest(
                PRODUCT_ID, BATCH_ID, WAREHOUSE_ID, "SANITARIO", BigDecimal.ONE, null)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    private void stubProductExists() {
        when(productRepo.findById(PRODUCT_ID)).thenReturn(Optional.of(product()));
    }

    private StockDisposal withId(StockDisposal d) {
        return new StockDisposal(
                UUID.randomUUID(), d.productId(), d.batchId(), d.warehouseId(),
                d.disposalType(), d.quantity(), d.unitCost(), d.reason(), d.createdBy(), OffsetDateTime.now());
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
