package co.posinvent.domain.service;

import co.posinvent.domain.model.CostLayer;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Behavioral parity guard (REQ-COST-005): the new pure strategies MUST produce
 * numerically IDENTICAL 6dp unit costs to the pre-refactor {@code CostingService}
 * algorithm. The legacy algorithm is reproduced inline as the golden reference
 * and run side-by-side with the new strategy on the same inputs.
 */
class CostingEquivalenceTest {

    private static final UUID PRODUCT_ID = UUID.randomUUID();
    private static final UUID BATCH_ID = UUID.randomUUID();
    private static final UUID WAREHOUSE_ID = UUID.randomUUID();

    @Test
    void fifoExitMatchesLegacyAlgorithm() {
        // Layer A: 5 @ $80, Layer B: 10 @ $90, Layer C: 10 @ $100 — consume 12
        var layers = List.of(
                layer("5", "80"),
                layer("10", "90"),
                layer("10", "100")
        );
        var quantity = new BigDecimal("12");

        var expected = legacyFifoConsume(layers, quantity);
        var actual = new FifoCostingStrategy().onExit(layers, quantity).consumedUnitCost();

        assertThat(actual).isEqualByComparingTo(expected);
        // Golden value: (5×80 + 7×90) / 12 = 1030 / 12 = 85.833333
        assertThat(actual).isEqualByComparingTo("85.833333");
    }

    @Test
    void weightedAvgEntryMatchesLegacyAlgorithm() {
        var existing = List.of(layer("20", "8.00"));
        var quantity = new BigDecimal("10");
        var unitCost = new BigDecimal("5.00");

        var expected = legacyWeightedAvgEntry(quantity, unitCost, existing);
        var entry = new StockEntry(
                PRODUCT_ID, BATCH_ID, WAREHOUSE_ID,
                quantity, unitCost, null, OffsetDateTime.now());
        var actual = new WeightedAvgCostingStrategy().onEntry(entry, existing).resultingUnitCost();

        assertThat(actual).isEqualByComparingTo(expected);
        // Golden value: (10×5 + 20×8) / 30 = 210 / 30 = 7.000000
        assertThat(actual).isEqualByComparingTo("7.000000");
    }

    @Test
    void weightedAvgRoundingMatchesLegacyAlgorithm() {
        var existing = List.of(layer("2", "0.00"));
        var quantity = new BigDecimal("1");
        var unitCost = new BigDecimal("10.00");

        var expected = legacyWeightedAvgEntry(quantity, unitCost, existing);
        var entry = new StockEntry(
                PRODUCT_ID, BATCH_ID, WAREHOUSE_ID,
                quantity, unitCost, null, OffsetDateTime.now());
        var actual = new WeightedAvgCostingStrategy().onEntry(entry, existing).resultingUnitCost();

        assertThat(actual).isEqualByComparingTo(expected);
        // Golden value: 10 / 3 = 3.333333 (6dp HALF_UP)
        assertThat(actual).isEqualByComparingTo("3.333333");
    }

    // ── Legacy reference implementations (pre-refactor CostingService) ──

    private static BigDecimal legacyFifoConsume(List<CostLayer> layers, BigDecimal quantity) {
        var remaining = quantity;
        var totalCost = BigDecimal.ZERO;
        var consumedQty = BigDecimal.ZERO;

        for (var layer : layers) {
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) break;
            var fromLayer = layer.remainingQuantity().min(remaining);
            totalCost = totalCost.add(fromLayer.multiply(layer.unitCost()));
            consumedQty = consumedQty.add(fromLayer);
            remaining = remaining.subtract(fromLayer);
        }

        return consumedQty.compareTo(BigDecimal.ZERO) > 0
                ? totalCost.divide(consumedQty, 6, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
    }

    private static BigDecimal legacyWeightedAvgEntry(
            BigDecimal quantity, BigDecimal unitCost, List<CostLayer> existing) {
        var totalQty = quantity;
        var totalValue = quantity.multiply(unitCost);

        for (var layer : existing) {
            totalQty = totalQty.add(layer.remainingQuantity());
            totalValue = totalValue.add(layer.remainingQuantity().multiply(layer.unitCost()));
        }

        return totalQty.compareTo(BigDecimal.ZERO) > 0
                ? totalValue.divide(totalQty, 6, RoundingMode.HALF_UP)
                : unitCost;
    }

    private static CostLayer layer(String qty, String cost) {
        return new CostLayer(
                UUID.randomUUID(), PRODUCT_ID, BATCH_ID, WAREHOUSE_ID,
                new BigDecimal(qty), new BigDecimal(cost),
                OffsetDateTime.now().minusDays(1), null);
    }
}
