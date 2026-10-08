package co.posinvent.application.usecase;

import co.posinvent.domain.model.Batch;
import co.posinvent.domain.model.InventoryMovement;
import co.posinvent.domain.model.InventoryStock;
import co.posinvent.domain.model.MovementType;
import co.posinvent.domain.repository.BatchRepository;
import co.posinvent.domain.repository.StockRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DisposeExpiredBatchUseCaseTest {

    @Mock
    private StockRepository stockRepository;

    @Mock
    private BatchRepository batchRepository;

    @Mock
    private RecordMovementUseCase recordMovement;

    @Mock
    private OptimisticConcurrencyExecutor concurrencyExecutor;

    private DisposeExpiredBatchUseCase useCase;

    private static final UUID BATCH_ID = UUID.randomUUID();
    private static final UUID PRODUCT_ID = UUID.randomUUID();
    private static final UUID WAREHOUSE_ID = UUID.randomUUID();
    private static final UUID STOCK_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        useCase = new DisposeExpiredBatchUseCase(
                stockRepository, batchRepository, recordMovement, concurrencyExecutor
        );
        // Run the transactional unit of work inline.
        doAnswer(inv -> ((Supplier<?>) inv.getArgument(0)).get())
                .when(concurrencyExecutor).execute(any());
    }

    @Test
    void disposesStockRecordsMovementAndClosesBatch() {
        var remainingQty = new BigDecimal("3.500");

        var stock = new InventoryStock(
                STOCK_ID, PRODUCT_ID, BATCH_ID, WAREHOUSE_ID,
                remainingQty, BigDecimal.ZERO, new BigDecimal("50.000000"),
                OffsetDateTime.now().minusDays(10), null
        );
        when(stockRepository.findByProductBatchWarehouse(PRODUCT_ID, BATCH_ID, WAREHOUSE_ID))
                .thenReturn(Optional.of(stock));
        doAnswer(inv -> inv.getArgument(0)).when(stockRepository).save(any(InventoryStock.class));

        when(recordMovement.record(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new InventoryMovement(
                        UUID.randomUUID(), PRODUCT_ID, BATCH_ID, WAREHOUSE_ID,
                        MovementType.DISPOSAL, remainingQty, BigDecimal.ZERO,
                        remainingQty, BigDecimal.ZERO,
                        "EXPIRATION", BATCH_ID, "Vencimiento automático", "SYSTEM", null
                ));

        var batch = new Batch(
                BATCH_ID, UUID.randomUUID(), UUID.randomUUID(), WAREHOUSE_ID,
                java.time.LocalDate.now().minusDays(60),
                remainingQty, new BigDecimal("175"),
                Batch.BatchStatus.OPEN, "Lote de prueba",
                java.time.LocalDate.now().minusDays(1),
                UUID.randomUUID(),
                OffsetDateTime.now().minusDays(60),
                OffsetDateTime.now().minusDays(1),
                null, null,
                null, null, null, null, null, null, null
        );
        when(batchRepository.findById(BATCH_ID)).thenReturn(Optional.of(batch));
        doAnswer(inv -> inv.getArgument(0)).when(batchRepository).save(any(Batch.class));

        useCase.execute(BATCH_ID, PRODUCT_ID, WAREHOUSE_ID, remainingQty);

        var stockCaptor = ArgumentCaptor.forClass(InventoryStock.class);
        verify(stockRepository).save(stockCaptor.capture());
        assertThat(stockCaptor.getValue().currentQuantity()).isEqualByComparingTo(BigDecimal.ZERO);

        var batchCaptor = ArgumentCaptor.forClass(Batch.class);
        verify(batchRepository).save(batchCaptor.capture());
        assertThat(batchCaptor.getValue().status()).isEqualTo(Batch.BatchStatus.CLOSED);

        verify(recordMovement).record(
                any(), any(), any(),
                any(), any(), any(), any(), any(),
                any(), any(), any()
        );
    }
}
