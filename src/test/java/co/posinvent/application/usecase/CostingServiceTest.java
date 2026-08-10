package co.posinvent.application.usecase;

import co.posinvent.domain.model.CostLayer;
import co.posinvent.domain.model.Product;
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
class CostingServiceTest {

    @Mock
    private CostLayerRepository layerRepo;

    @Mock
    private ProductRepository productRepo;

    @Mock
    private StockRepository stockRepo;

    private CostingService costingService;

    private static final UUID PRODUCT_ID = UUID.randomUUID();
    private static final UUID BATCH_ID = UUID.randomUUID();
    private static final UUID WAREHOUSE_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        costingService = new CostingService(layerRepo, productRepo, stockRepo);
    }

    @Test
    void shouldConsumeCostLayersInExpirationDateOrder() {
        // ── Arrange ─────────────────────────────────────────────────────
        // Product with PEPS costing method
        var product = pepsProduct(PRODUCT_ID);
        when(productRepo.findById(PRODUCT_ID)).thenReturn(Optional.of(product));

        // Layers returned in expiration date order (oldest first) by the
        // findByProductBatchWarehouseFefo repository method:
        // Layer A: expires 2026-01-15 (oldest) — 5 units at $80
        // Layer B: expires 2026-06-30 (newer) — 10 units at $90
        // Layer C: expires 2026-12-01 (newest) — 10 units at $100
        var layerA = new CostLayer(
                UUID.randomUUID(), PRODUCT_ID, BATCH_ID, WAREHOUSE_ID,
                new BigDecimal("5"), new BigDecimal("80"),
                OffsetDateTime.now().minusDays(1), null
        );
        var layerB = new CostLayer(
                UUID.randomUUID(), PRODUCT_ID, BATCH_ID, WAREHOUSE_ID,
                new BigDecimal("10"), new BigDecimal("90"),
                OffsetDateTime.now().minusDays(1), null
        );
        var layerC = new CostLayer(
                UUID.randomUUID(), PRODUCT_ID, BATCH_ID, WAREHOUSE_ID,
                new BigDecimal("10"), new BigDecimal("100"),
                OffsetDateTime.now().minusDays(1), null
        );

        // Repository returns layers in expiration order
        when(layerRepo.findByProductBatchWarehouseFefo(PRODUCT_ID, BATCH_ID, WAREHOUSE_ID))
                .thenReturn(List.of(layerA, layerB, layerC));

        doAnswer(inv -> inv.getArgument(0)).when(layerRepo).save(any(CostLayer.class));

        // ── Act: consume 12 units ───────────────────────────────────────
        BigDecimal consumedCost = costingService.resolveCostOnExit(
                PRODUCT_ID, BATCH_ID, WAREHOUSE_ID, new BigDecimal("12")
        );

        // ── Assert ──────────────────────────────────────────────────────
        // Consumption order:
        //   Layer A: 5 units × $80 = $400  (fully consumed → remaining = 0)
        //   Layer B: 7 units × $90 = $630  (partially consumed → remaining = 3)
        //   Layer C: 0 units               (not touched)
        // Weighted average cost = (400 + 630) / 12 = 85.833333...

        assertThat(consumedCost).isEqualByComparingTo("85.833333");

        // Verify layer saves: layerA set to 0, layerB set to 3, layerC untouched
        var layerCaptor = ArgumentCaptor.forClass(CostLayer.class);
        verify(layerRepo, times(2)).save(layerCaptor.capture());
        var savedLayers = layerCaptor.getAllValues();

        // layerA fully consumed — remaining = 0
        assertThat(savedLayers.get(0).remainingQuantity()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(savedLayers.get(0).unitCost()).isEqualByComparingTo("80");
        // layerB partially consumed — remaining = 3
        assertThat(savedLayers.get(1).remainingQuantity()).isEqualByComparingTo("3");
        assertThat(savedLayers.get(1).unitCost()).isEqualByComparingTo("90");
    }

    // ── Helper ──────────────────────────────────────────────────────────

    private Product pepsProduct(UUID id) {
        return new Product(
                id, "P-PEPS", "Producto PEPS",
                null, null, null, null, null, null, null, null, null, null,
                BigDecimal.ZERO, BigDecimal.ZERO, "EXENTO", BigDecimal.TEN,
                "PEPS",  // ← PEPS costing method
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.TEN, BigDecimal.ZERO,
                false, false, false, false, false, false, true,
                null, null, null, null, null, null, null,
                true, 0,
                OffsetDateTime.now().minusDays(2), OffsetDateTime.now().minusDays(1),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of()
        );
    }
}
