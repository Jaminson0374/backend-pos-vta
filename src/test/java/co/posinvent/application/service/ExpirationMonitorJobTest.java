package co.posinvent.application.service;

import co.posinvent.application.usecase.DisposeExpiredBatchUseCase;
import co.posinvent.domain.repository.StockDisposalRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExpirationMonitorJobTest {

    @Mock
    private StockDisposalRepository disposalRepo;

    @Mock
    private DisposeExpiredBatchUseCase disposeExpiredBatch;

    private ExpirationMonitorJob job;

    private static final UUID BATCH_ID = UUID.randomUUID();
    private static final UUID PRODUCT_ID = UUID.randomUUID();
    private static final UUID WAREHOUSE_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        job = new ExpirationMonitorJob(disposalRepo, disposeExpiredBatch);
    }

    private static Map<String, Object> expiredRow() {
        var row = new HashMap<String, Object>();
        row.put("batch_id", BATCH_ID);
        row.put("product_id", PRODUCT_ID);
        row.put("warehouse_id", WAREHOUSE_ID);
        row.put("product_name", "Producto");
        row.put("warehouse_name", "Bodega");
        row.put("expiration_date", OffsetDateTime.now().minusDays(1));
        row.put("current_qty", new BigDecimal("3.500"));
        return row;
    }

    @Test
    void delegatesExpiredBatchDisposalToUseCaseWhenAutoDisposeEnabled() {
        ReflectionTestUtils.setField(job, "autoDispose", true);
        when(disposalRepo.findExpiredBatches()).thenReturn(List.of(expiredRow()));

        job.checkExpiringBatches();

        verify(disposeExpiredBatch).execute(BATCH_ID, PRODUCT_ID, WAREHOUSE_ID, new BigDecimal("3.500"));
    }

    @Test
    void doesNotDisposeWhenAutoDisposeDisabled() {
        ReflectionTestUtils.setField(job, "autoDispose", false);
        when(disposalRepo.findExpiredBatches()).thenReturn(List.of(expiredRow()));

        job.checkExpiringBatches();

        verifyNoInteractions(disposeExpiredBatch);
    }
}
