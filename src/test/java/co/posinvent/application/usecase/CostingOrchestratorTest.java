package co.posinvent.application.usecase;

import co.posinvent.domain.model.CompanyConfig;
import co.posinvent.domain.model.CostLayer;
import co.posinvent.domain.model.Product;
import co.posinvent.domain.repository.CompanyConfigRepository;
import co.posinvent.domain.repository.CostLayerRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CostingOrchestratorTest {

    @Mock
    private CostLayerRepository layerRepo;

    @Mock
    private ProductRepository productRepo;

    @Mock
    private StockRepository stockRepo;

    @Mock
    private CompanyConfigRepository configRepo;

    private CostingOrchestrator orchestrator;

    private static final UUID PRODUCT_ID = UUID.randomUUID();
    private static final UUID BATCH_ID = UUID.randomUUID();
    private static final UUID WAREHOUSE_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        orchestrator = new CostingOrchestrator(layerRepo, productRepo, stockRepo, configRepo);
    }

    @Test
    void shouldConsumeCostLayersInExpirationDateOrder() {
        // ── Arrange ─────────────────────────────────────────────────────
        // Global config unset → deterministic FIFO fallback.
        when(productRepo.findById(PRODUCT_ID)).thenReturn(Optional.of(product(PRODUCT_ID)));
        when(configRepo.findConfig()).thenReturn(Optional.empty());

        // Layers returned in expiration date order (oldest first) by the
        // findByProductBatchWarehouseFefo repository method:
        // Layer A: expires 2026-01-15 (oldest) — 5 units at $80
        // Layer B: expires 2026-06-30 (newer) — 10 units at $90
        // Layer C: expires 2026-12-01 (newest) — 10 units at $100
        var layerA = layer(UUID.randomUUID(), "5", "80");
        var layerB = layer(UUID.randomUUID(), "10", "90");
        var layerC = layer(UUID.randomUUID(), "10", "100");

        when(layerRepo.findByProductBatchWarehouseFefo(PRODUCT_ID, BATCH_ID, WAREHOUSE_ID))
                .thenReturn(List.of(layerA, layerB, layerC));

        doAnswer(inv -> inv.getArgument(0)).when(layerRepo).save(any(CostLayer.class));

        // ── Act: consume 12 units ───────────────────────────────────────
        BigDecimal consumedCost = orchestrator.resolveCostOnExit(
                PRODUCT_ID, BATCH_ID, WAREHOUSE_ID, new BigDecimal("12")
        );

        // ── Assert ──────────────────────────────────────────────────────
        // Layer A: 5×$80 = $400 (fully consumed → deleted)
        // Layer B: 7×$90 = $630 (partially consumed → remaining 3)
        // Weighted average = (400 + 630) / 12 = 85.833333...
        assertThat(consumedCost).isEqualByComparingTo("85.833333");

        // Layer A fully consumed → deleted; Layer B partially → saved (remaining 3)
        verify(layerRepo).deleteById(layerA.id());
        verify(layerRepo, never()).deleteById(layerB.id());
        verify(layerRepo, never()).deleteById(layerC.id());

        var layerCaptor = ArgumentCaptor.forClass(CostLayer.class);
        verify(layerRepo, times(1)).save(layerCaptor.capture());
        assertThat(layerCaptor.getValue().remainingQuantity()).isEqualByComparingTo("3");
        assertThat(layerCaptor.getValue().unitCost()).isEqualByComparingTo("90");
    }

    @Test
    void shouldRecalculateWeightedAverageOnEntry() {
        // ── Arrange ─────────────────────────────────────────────────────
        when(productRepo.findById(PRODUCT_ID)).thenReturn(Optional.of(product(PRODUCT_ID)));
        when(configRepo.findConfig()).thenReturn(Optional.of(companyConfig("WEIGHTED_AVG")));

        // Existing merged layer 20 @ 8.00
        var existing = layer(UUID.randomUUID(), "20", "8.00");
        when(layerRepo.findByProductBatchWarehouse(PRODUCT_ID, BATCH_ID, WAREHOUSE_ID))
                .thenReturn(List.of(existing));

        // ── Act: enter 10 @ 5.00 ────────────────────────────────────────
        BigDecimal unitCost = orchestrator.resolveCostOnEntry(
                PRODUCT_ID, BATCH_ID, WAREHOUSE_ID,
                new BigDecimal("10"), new BigDecimal("5.00"), null
        );

        // ── Assert: (10×5 + 20×8) / 30 = 7.000000 ───────────────────────
        assertThat(unitCost).isEqualByComparingTo("7.000000");

        verify(layerRepo).deleteAllByProductBatchWarehouse(PRODUCT_ID, BATCH_ID, WAREHOUSE_ID);
        var layerCaptor = ArgumentCaptor.forClass(CostLayer.class);
        verify(layerRepo).save(layerCaptor.capture());
        assertThat(layerCaptor.getValue().remainingQuantity()).isEqualByComparingTo("30");
        assertThat(layerCaptor.getValue().unitCost()).isEqualByComparingTo("7.000000");
    }

    @Test
    void shouldFallBackToFifoWhenConfigCostingMethodIsBlank() {
        // ── Arrange ─────────────────────────────────────────────────────
        when(productRepo.findById(PRODUCT_ID)).thenReturn(Optional.of(product(PRODUCT_ID)));
        when(configRepo.findConfig()).thenReturn(Optional.of(companyConfig("   ")));

        var layerA = layer(UUID.randomUUID(), "5", "80");
        when(layerRepo.findByProductBatchWarehouseFefo(PRODUCT_ID, BATCH_ID, WAREHOUSE_ID))
                .thenReturn(List.of(layerA));

        // ── Act: consume the full 5 units ───────────────────────────────
        BigDecimal consumedCost = orchestrator.resolveCostOnExit(
                PRODUCT_ID, BATCH_ID, WAREHOUSE_ID, new BigDecimal("5")
        );

        // ── Assert: blank → FIFO fallback → FEFO path, single layer consumed ──
        assertThat(consumedCost).isEqualByComparingTo("80.00");
        verify(layerRepo).deleteById(layerA.id());
        verify(layerRepo, never()).findByProductBatchWarehouse(any(), any(), any());
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private CostLayer layer(UUID id, String qty, String cost) {
        return new CostLayer(
                id, PRODUCT_ID, BATCH_ID, WAREHOUSE_ID,
                new BigDecimal(qty), new BigDecimal(cost),
                OffsetDateTime.now().minusDays(1), null
        );
    }

    private Product product(UUID id) {
        return new Product(
                id, "P-PRODUCTO", "Producto",
                null, null, null, null, null, null, null, null, null, null,
                BigDecimal.ZERO, BigDecimal.ZERO, "EXENTO", BigDecimal.TEN,
                "PEPS",  // ← legacy value ignored; global config drives resolution
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.TEN, BigDecimal.ZERO,
                false, false, false, false, false, false, true,
                null, null, null, null, null, null, null,
                true, 0,
                OffsetDateTime.now().minusDays(2), OffsetDateTime.now().minusDays(1),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of()
        );
    }

    private CompanyConfig companyConfig(String costingMethod) {
        return new CompanyConfig(
                1L, "Empresa", "NIT", "Dir", "Tel", "mail@example.com",
                "Actividad", "Régimen", "COP", null,
                null, BigDecimal.ZERO, 0, null,
                costingMethod, null, BigDecimal.ZERO,
                null, null, null,
                false, BigDecimal.ZERO, null, null
        );
    }
}
