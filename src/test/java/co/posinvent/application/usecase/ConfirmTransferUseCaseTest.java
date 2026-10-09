package co.posinvent.application.usecase;

import co.posinvent.domain.exception.BusinessException;
import co.posinvent.domain.model.InventoryStock;
import co.posinvent.domain.model.MovementType;
import co.posinvent.domain.model.StockTransfer;
import co.posinvent.domain.model.StockTransferItem;
import co.posinvent.domain.model.TransferStatus;
import co.posinvent.domain.repository.ProductRepository;
import co.posinvent.domain.repository.StockRepository;
import co.posinvent.domain.repository.StockTransferRepository;
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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConfirmTransferUseCaseTest {

    @Mock
    private StockTransferRepository transferRepo;

    @Mock
    private StockRepository stockRepo;

    @Mock
    private ProductRepository productRepo;

    @Mock
    private RecordMovementUseCase recordMovement;

    @Mock
    private OptimisticConcurrencyExecutor concurrencyExecutor;

    private ConfirmTransferUseCase useCase;

    private static final UUID TRANSFER_ID = UUID.randomUUID();
    private static final UUID SOURCE_WAREHOUSE_ID = UUID.randomUUID();
    private static final UUID TARGET_WAREHOUSE_ID = UUID.randomUUID();
    private static final UUID PRODUCT_ID = UUID.randomUUID();
    private static final UUID BATCH_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        useCase = new ConfirmTransferUseCase(
                transferRepo, stockRepo, productRepo, recordMovement, concurrencyExecutor);
        doAnswer(inv -> ((Supplier<?>) inv.getArgument(0)).get())
                .when(concurrencyExecutor).execute(any());
    }

    @Test
    void execute_decrementsSourceCreditsTargetAndRecordsBothMovements() {
        var transfer = transfer(TransferStatus.DRAFT, List.of(item("4")));
        when(transferRepo.findById(TRANSFER_ID)).thenReturn(Optional.of(transfer));

        when(stockRepo.findByProductBatchWarehouse(PRODUCT_ID, BATCH_ID, SOURCE_WAREHOUSE_ID))
                .thenReturn(Optional.of(stock("10", "5")));
        when(stockRepo.findByProductBatchWarehouse(PRODUCT_ID, BATCH_ID, TARGET_WAREHOUSE_ID))
                .thenReturn(Optional.empty());
        doAnswer(inv -> inv.getArgument(0)).when(stockRepo).save(any(InventoryStock.class));
        doAnswer(inv -> inv.getArgument(0)).when(transferRepo).save(any(StockTransfer.class));

        var response = useCase.execute(TRANSFER_ID);

        assertThat(response.status()).isEqualTo(TransferStatus.CONFIRMED.name());

        var stockCaptor = ArgumentCaptor.forClass(InventoryStock.class);
        verify(stockRepo, times(2)).save(stockCaptor.capture());
        var savedStocks = stockCaptor.getAllValues();
        // Source decremented 10 -> 6
        assertThat(savedStocks.get(0).warehouseId()).isEqualTo(SOURCE_WAREHOUSE_ID);
        assertThat(savedStocks.get(0).currentQuantity()).isEqualByComparingTo("6");
        // Target created with 4
        assertThat(savedStocks.get(1).warehouseId()).isEqualTo(TARGET_WAREHOUSE_ID);
        assertThat(savedStocks.get(1).id()).isNull();
        assertThat(savedStocks.get(1).currentQuantity()).isEqualByComparingTo("4");

        verify(recordMovement).record(
                eq(PRODUCT_ID), eq(BATCH_ID), eq(SOURCE_WAREHOUSE_ID), eq(MovementType.TRANSFER_OUT),
                eq(new BigDecimal("4")), eq(new BigDecimal("5")),
                eq(new BigDecimal("10")), eq(new BigDecimal("6")),
                eq("TRANSFER"), eq(TRANSFER_ID), eq("Traslado salida"));

        verify(recordMovement).record(
                eq(PRODUCT_ID), eq(BATCH_ID), eq(TARGET_WAREHOUSE_ID), eq(MovementType.TRANSFER_IN),
                eq(new BigDecimal("4")), eq(new BigDecimal("5")),
                eq(BigDecimal.ZERO), eq(new BigDecimal("4")),
                eq("TRANSFER"), eq(TRANSFER_ID), eq("Traslado entrada"));

        verify(productRepo).recalculateTotalStock(PRODUCT_ID);
    }

    @Test
    void execute_creditsExistingTargetStock() {
        var transfer = transfer(TransferStatus.DRAFT, List.of(item("4")));
        when(transferRepo.findById(TRANSFER_ID)).thenReturn(Optional.of(transfer));

        when(stockRepo.findByProductBatchWarehouse(PRODUCT_ID, BATCH_ID, SOURCE_WAREHOUSE_ID))
                .thenReturn(Optional.of(stock("10", "5")));
        when(stockRepo.findByProductBatchWarehouse(PRODUCT_ID, BATCH_ID, TARGET_WAREHOUSE_ID))
                .thenReturn(Optional.of(targetStock("2")));
        doAnswer(inv -> inv.getArgument(0)).when(stockRepo).save(any(InventoryStock.class));
        doAnswer(inv -> inv.getArgument(0)).when(transferRepo).save(any(StockTransfer.class));

        useCase.execute(TRANSFER_ID);

        var stockCaptor = ArgumentCaptor.forClass(InventoryStock.class);
        verify(stockRepo, times(2)).save(stockCaptor.capture());
        assertThat(stockCaptor.getAllValues().get(1).currentQuantity()).isEqualByComparingTo("6");

        verify(recordMovement).record(
                eq(PRODUCT_ID), eq(BATCH_ID), eq(TARGET_WAREHOUSE_ID), eq(MovementType.TRANSFER_IN),
                eq(new BigDecimal("4")), eq(new BigDecimal("5")),
                eq(new BigDecimal("2")), eq(new BigDecimal("6")),
                eq("TRANSFER"), eq(TRANSFER_ID), eq("Traslado entrada"));
    }

    @Test
    void execute_rejectsWhenTransferIsNotDraft() {
        var transfer = transfer(TransferStatus.CONFIRMED, List.of());
        when(transferRepo.findById(TRANSFER_ID)).thenReturn(Optional.of(transfer));

        assertThatThrownBy(() -> useCase.execute(TRANSFER_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", "TRANSFER_NOT_DRAFT");

        verify(stockRepo, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void execute_rejectsWhenSourceStockIsInsufficient() {
        var transfer = transfer(TransferStatus.DRAFT, List.of(item("10")));
        when(transferRepo.findById(TRANSFER_ID)).thenReturn(Optional.of(transfer));
        when(stockRepo.findByProductBatchWarehouse(PRODUCT_ID, BATCH_ID, SOURCE_WAREHOUSE_ID))
                .thenReturn(Optional.of(stock("4", "5")));

        assertThatThrownBy(() -> useCase.execute(TRANSFER_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", "TRANSFER_NO_STOCK");

        verify(stockRepo, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void execute_rejectsWhenSourceStockIsMissing() {
        var transfer = transfer(TransferStatus.DRAFT, List.of(item("1")));
        when(transferRepo.findById(TRANSFER_ID)).thenReturn(Optional.of(transfer));
        when(stockRepo.findByProductBatchWarehouse(PRODUCT_ID, BATCH_ID, SOURCE_WAREHOUSE_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(TRANSFER_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", "TRANSFER_NO_STOCK");
    }

    private StockTransfer transfer(TransferStatus status, List<StockTransferItem> items) {
        return new StockTransfer(
                TRANSFER_ID, SOURCE_WAREHOUSE_ID, TARGET_WAREHOUSE_ID, status,
                "Traslado", "SYSTEM", OffsetDateTime.now().minusHours(1), null, null, items);
    }

    private StockTransferItem item(String quantity) {
        return new StockTransferItem(
                UUID.randomUUID(), TRANSFER_ID, PRODUCT_ID, BATCH_ID, new BigDecimal(quantity), BigDecimal.ZERO);
    }

    private InventoryStock stock(String quantity, String unitCost) {
        return new InventoryStock(
                UUID.randomUUID(), PRODUCT_ID, BATCH_ID, SOURCE_WAREHOUSE_ID,
                new BigDecimal(quantity), BigDecimal.ZERO, new BigDecimal(unitCost),
                OffsetDateTime.now().minusDays(1), OffsetDateTime.now().minusHours(1));
    }

    private InventoryStock targetStock(String quantity) {
        return new InventoryStock(
                UUID.randomUUID(), PRODUCT_ID, BATCH_ID, TARGET_WAREHOUSE_ID,
                new BigDecimal(quantity), BigDecimal.ZERO, new BigDecimal("5"),
                OffsetDateTime.now().minusDays(1), OffsetDateTime.now().minusHours(1));
    }
}
