package co.posinvent.domain.service;

import co.posinvent.domain.exception.BusinessException;
import co.posinvent.domain.model.CostLayer;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FifoCostingStrategyTest {

    private final FifoCostingStrategy strategy = new FifoCostingStrategy();

    private static final UUID PRODUCT_ID = UUID.randomUUID();
    private static final UUID BATCH_ID = UUID.randomUUID();
    private static final UUID WAREHOUSE_ID = UUID.randomUUID();

    @Test
    void method_returnsFifo() {
        assertThat(strategy.method()).isEqualTo(CostingMethod.FIFO);
    }

    @Test
    void onEntry_createsSingleNewLayerAtEntryCost() {
        var sourceMovementId = UUID.randomUUID();
        var entryDate = OffsetDateTime.now();
        var entry = new StockEntry(
                PRODUCT_ID, BATCH_ID, WAREHOUSE_ID,
                new BigDecimal("10"), new BigDecimal("6.00"),
                sourceMovementId, entryDate
        );

        var result = strategy.onEntry(entry, List.of());

        assertThat(result.resultingUnitCost()).isEqualByComparingTo("6.00");
        assertThat(result.replaceExisting()).isFalse();
        assertThat(result.layersToUpsert()).hasSize(1);

        var layer = result.layersToUpsert().get(0);
        assertThat(layer.id()).isNull();
        assertThat(layer.productId()).isEqualTo(PRODUCT_ID);
        assertThat(layer.batchId()).isEqualTo(BATCH_ID);
        assertThat(layer.warehouseId()).isEqualTo(WAREHOUSE_ID);
        assertThat(layer.remainingQuantity()).isEqualByComparingTo("10");
        assertThat(layer.unitCost()).isEqualByComparingTo("6.00");
        assertThat(layer.sourceMovementId()).isEqualTo(sourceMovementId);
        assertThat(layer.entryDate()).isEqualTo(entryDate);
    }

    @Test
    void onExit_consumesAcrossTwoLayersInFefoOrder() {
        var l1 = layer(UUID.randomUUID(), new BigDecimal("5"), new BigDecimal("6.00"));
        var l2 = layer(UUID.randomUUID(), new BigDecimal("10"), new BigDecimal("7.00"));

        var result = strategy.onExit(List.of(l1, l2), new BigDecimal("12"));

        // (5×6.00 + 7×7.00) / 12 = 79 / 12 = 6.583333
        assertThat(result.consumedUnitCost()).isEqualByComparingTo("6.583333");
        assertThat(result.consumedUnitCost().scale()).isEqualTo(6);

        // L1 fully consumed → deleted; L2 partially consumed (3 remaining) → upserted
        assertThat(result.layerIdsToDelete()).containsExactly(l1.id());
        assertThat(result.layersToUpsert()).hasSize(1);
        var upserted = result.layersToUpsert().get(0);
        assertThat(upserted.id()).isEqualTo(l2.id());
        assertThat(upserted.remainingQuantity()).isEqualByComparingTo("3");
        assertThat(upserted.unitCost()).isEqualByComparingTo("7.00");
    }

    @Test
    void onExit_rejectsWhenInsufficientStock() {
        var l1 = layer(UUID.randomUUID(), new BigDecimal("5"), new BigDecimal("6.00"));

        assertThatThrownBy(() -> strategy.onExit(List.of(l1), new BigDecimal("10")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", "INSUFFICIENT_STOCK");
    }

    @Test
    void onExit_fullyConsumesSingleLayer() {
        var l1 = layer(UUID.randomUUID(), new BigDecimal("5"), new BigDecimal("6.00"));

        var result = strategy.onExit(List.of(l1), new BigDecimal("5"));

        assertThat(result.consumedUnitCost()).isEqualByComparingTo("6.00");
        assertThat(result.layerIdsToDelete()).containsExactly(l1.id());
        assertThat(result.layersToUpsert()).isEmpty();
    }

    @Test
    void onExit_partiallyConsumesSingleLayer() {
        var l1 = layer(UUID.randomUUID(), new BigDecimal("10"), new BigDecimal("7.00"));

        var result = strategy.onExit(List.of(l1), new BigDecimal("4"));

        assertThat(result.consumedUnitCost()).isEqualByComparingTo("7.00");
        assertThat(result.layerIdsToDelete()).isEmpty();
        assertThat(result.layersToUpsert()).hasSize(1);
        assertThat(result.layersToUpsert().get(0).remainingQuantity()).isEqualByComparingTo("6");
    }

    private CostLayer layer(UUID id, BigDecimal qty, BigDecimal cost) {
        return new CostLayer(
                id, PRODUCT_ID, BATCH_ID, WAREHOUSE_ID, qty, cost, OffsetDateTime.now(), null);
    }
}
