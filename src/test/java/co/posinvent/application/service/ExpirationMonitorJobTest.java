package co.posinvent.application.service;

import co.posinvent.application.usecase.RecordMovementUseCase;
import co.posinvent.domain.model.Batch;
import co.posinvent.domain.model.InventoryMovement;
import co.posinvent.domain.model.InventoryStock;
import co.posinvent.domain.model.MovementType;
import co.posinvent.domain.repository.BatchRepository;
import co.posinvent.domain.repository.StockDisposalRepository;
import co.posinvent.domain.repository.StockRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExpirationMonitorJobTest {

    @Mock
    private StockDisposalRepository disposalRepo;

    @Mock
    private StockRepository stockRepository;

    @Mock
    private BatchRepository batchRepository;

    @Mock
    private RecordMovementUseCase recordMovement;

    private ExpirationMonitorJob job;

    private static final UUID BATCH_ID = UUID.randomUUID();
    private static final UUID PRODUCT_ID = UUID.randomUUID();
    private static final UUID WAREHOUSE_ID = UUID.randomUUID();
    private static final UUID STOCK_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        job = new ExpirationMonitorJob(
                disposalRepo, stockRepository, batchRepository, recordMovement
        );
    }

    @Test
    void shouldDisposeAndCloseBatchWhenAutoDisposeIsTrueAndBatchExpired() {
        // Enable auto-dispose
        ReflectionTestUtils.setField(job, "autoDispose", true);

        var remainingQty = new BigDecimal("3.500");

        // Stock exists with quantity > 0
        var stock = new InventoryStock(
                STOCK_ID, PRODUCT_ID, BATCH_ID, WAREHOUSE_ID,
                remainingQty, BigDecimal.ZERO, new BigDecimal("50.000000"),
                OffsetDateTime.now().minusDays(10), null
        );
        when(stockRepository.findByProductBatchWarehouse(PRODUCT_ID, BATCH_ID, WAREHOUSE_ID))
                .thenReturn(Optional.of(stock));

        // Stock save returns what is passed
        doAnswer(inv -> inv.getArgument(0)).when(stockRepository).save(any(InventoryStock.class));

        // Record movement
        when(recordMovement.record(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new InventoryMovement(
                        UUID.randomUUID(), PRODUCT_ID, BATCH_ID, WAREHOUSE_ID,
                        MovementType.DISPOSAL, remainingQty, BigDecimal.ZERO,
                        remainingQty, BigDecimal.ZERO,
                        "EXPIRATION", BATCH_ID, "Vencimiento automático", "SYSTEM", null
                ));

        // Batch exists
        var batch = new Batch(
                BATCH_ID, UUID.randomUUID(), UUID.randomUUID(), WAREHOUSE_ID,
                java.time.LocalDate.now().minusDays(60),
                remainingQty, new BigDecimal("175"),
                Batch.BatchStatus.OPEN, "Lote de prueba",
                java.time.LocalDate.now().minusDays(1), // expired yesterday
                UUID.randomUUID(),
                OffsetDateTime.now().minusDays(60),
                OffsetDateTime.now().minusDays(1),
                null, null,
                null, null, null, null, null, null, null
        );
        when(batchRepository.findById(BATCH_ID)).thenReturn(Optional.of(batch));
        doAnswer(inv -> inv.getArgument(0)).when(batchRepository).save(any(Batch.class));

        // ── Act ─────────────────────────────────────────────────────────
        job.disposeExpiredBatch(BATCH_ID, PRODUCT_ID, WAREHOUSE_ID, remainingQty);

        // ── Assert ──────────────────────────────────────────────────────

        // Stock saved with quantity = 0
        var stockCaptor = ArgumentCaptor.forClass(InventoryStock.class);
        verify(stockRepository).save(stockCaptor.capture());
        assertThat(stockCaptor.getValue().currentQuantity()).isEqualByComparingTo(BigDecimal.ZERO);

        // Batch saved with CLOSED status
        var batchCaptor = ArgumentCaptor.forClass(Batch.class);
        verify(batchRepository).save(batchCaptor.capture());
        assertThat(batchCaptor.getValue().status()).isEqualTo(Batch.BatchStatus.CLOSED);

        // Record movement with DISPOSAL type
        verify(recordMovement).record(
                any(), any(), any(),
                any(), any(), any(), any(), any(),
                any(), any(), any()
        );
    }
}
