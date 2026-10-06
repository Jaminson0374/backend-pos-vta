package co.posinvent.domain.service;

import co.posinvent.domain.model.CostLayer;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class WeightedAvgCostingStrategyTest {

    private final WeightedAvgCostingStrategy strategy = new WeightedAvgCostingStrategy();

    private static final UUID PRODUCT_ID = UUID.randomUUID();
    private static final UUID BATCH_ID = UUID.randomUUID();
    private static final UUID WAREHOUSE_ID = UUID.randomUUID();

    @Test
    void method_returnsWeightedAvg() {
        assertThat(strategy.method()).isEqualTo(CostingMethod.WEIGHTED_AVG);
    }

    @Test
    void onEntry_mergesTwoLayersIntoSingleAveragedLayer() {
        // incoming 10 @ 5.00 + existing 20 @ 8.00 → (10×5 + 20×8)/30 = 7.000000
        var entry = new StockEntry(
                PRODUCT_ID, BATCH_ID, WAREHOUSE_ID,
                new BigDecimal("10"), new BigDecimal("5.00"),
                UUID.randomUUID(), OffsetDateTime.now());
        var existing = layer(UUID.randomUUID(), new BigDecimal("20"), new BigDecimal("8.00"));

        var result = strategy.onEntry(entry, List.of(existing));

        assertThat(result.resultingUnitCost()).isEqualByComparingTo("7.000000");
        assertThat(result.resultingUnitCost().scale()).isEqualTo(6);
        assertThat(result.replaceExisting()).isTrue();

        assertThat(result.layersToUpsert()).hasSize(1);
        var merged = result.layersToUpsert().get(0);
        assertThat(merged.id()).isNull();
        assertThat(merged.remainingQuantity()).isEqualByComparingTo("30");
        assertThat(merged.unitCost()).isEqualByComparingTo("7.000000");
        assertThat(merged.sourceMovementId()).isNull();
    }

    @Test
    void onEntry_roundsToSixDecimalsHalfUp() {
        // incoming 1 @ 10.00 + existing 2 @ 0.00 → 10/3 = 3.333333 (6dp HALF_UP)
        var entry = new StockEntry(
                PRODUCT_ID, BATCH_ID, WAREHOUSE_ID,
                new BigDecimal("1"), new BigDecimal("10.00"),
                UUID.randomUUID(), OffsetDateTime.now());
        var existing = layer(UUID.randomUUID(), new BigDecimal("2"), new BigDecimal("0.00"));

        var result = strategy.onEntry(entry, List.of(existing));

        assertThat(result.resultingUnitCost()).isEqualByComparingTo("3.333333");
        assertThat(result.resultingUnitCost().scale()).isEqualTo(6);
    }

    @Test
    void onEntry_withNoExistingLayersUsesEntryCost() {
        var entry = new StockEntry(
                PRODUCT_ID, BATCH_ID, WAREHOUSE_ID,
                new BigDecimal("4"), new BigDecimal("9.50"),
                UUID.randomUUID(), OffsetDateTime.now());

        var result = strategy.onEntry(entry, List.of());

        assertThat(result.resultingUnitCost()).isEqualByComparingTo("9.50");
        assertThat(result.replaceExisting()).isTrue();
        assertThat(result.layersToUpsert()).hasSize(1);
        assertThat(result.layersToUpsert().get(0).remainingQuantity()).isEqualByComparingTo("4");
    }

    @Test
    void onExit_partiallyConsumesSingleLayer() {
        var layer = layer(UUID.randomUUID(), new BigDecimal("10"), new BigDecimal("7.00"));

        var result = strategy.onExit(List.of(layer), new BigDecimal("4"));

        assertThat(result.consumedUnitCost()).isEqualByComparingTo("7.00");
        assertThat(result.layerIdsToDelete()).isEmpty();
        assertThat(result.layersToUpsert()).hasSize(1);
        var upserted = result.layersToUpsert().get(0);
        assertThat(upserted.id()).isEqualTo(layer.id());
        assertThat(upserted.remainingQuantity()).isEqualByComparingTo("6");
        assertThat(upserted.unitCost()).isEqualByComparingTo("7.00");
    }

    @Test
    void onExit_fullyConsumesSingleLayer() {
        var layer = layer(UUID.randomUUID(), new BigDecimal("10"), new BigDecimal("7.00"));

        var result = strategy.onExit(List.of(layer), new BigDecimal("10"));

        assertThat(result.consumedUnitCost()).isEqualByComparingTo("7.00");
        assertThat(result.layersToUpsert()).isEmpty();
        assertThat(result.layerIdsToDelete()).containsExactly(layer.id());
    }

    @Test
    void onExit_emptyLayersReturnsZeroCost() {
        var result = strategy.onExit(List.of(), new BigDecimal("5"));

        assertThat(result.consumedUnitCost()).isEqualByComparingTo("0");
        assertThat(result.layersToUpsert()).isEmpty();
        assertThat(result.layerIdsToDelete()).isEmpty();
    }

    private CostLayer layer(UUID id, BigDecimal qty, BigDecimal cost) {
        return new CostLayer(
                id, PRODUCT_ID, BATCH_ID, WAREHOUSE_ID, qty, cost, OffsetDateTime.now(), null);
    }
}
