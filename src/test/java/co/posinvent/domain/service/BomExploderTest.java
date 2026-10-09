package co.posinvent.domain.service;

import co.posinvent.domain.model.InventoryStock;
import co.posinvent.domain.model.ProductFormula;
import co.posinvent.domain.repository.ProductFormulaRepository;
import co.posinvent.domain.repository.StockRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BomExploderTest {

    @Mock
    private ProductFormulaRepository formulaRepo;

    @Mock
    private StockRepository stockRepository;

    private BomExploder exploder;

    private static final UUID WAREHOUSE_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        exploder = new BomExploder(formulaRepo, stockRepository);
    }

    @Test
    void explode_nonManufacturableComponent_isAlwaysLeaf() {
        var product = UUID.randomUUID();
        var rawMaterial = UUID.randomUUID();

        when(formulaRepo.findByParentProductId(product))
                .thenReturn(List.of(formula(product, rawMaterial, "2")));
        when(formulaRepo.findByParentProductId(rawMaterial)).thenReturn(List.of());

        var result = exploder.explode(product, WAREHOUSE_ID, new BigDecimal("3"));

        // needed = 2 * 3 = 6, depth 0
        assertThat(result).containsExactly(
                new BomExploder.ExplodedComponent(rawMaterial, new BigDecimal("6"), 0));
    }

    @Test
    void explode_manufacturableIntermediateWithStock_isLeaf() {
        var product = UUID.randomUUID();
        var intermediate = UUID.randomUUID();
        var rawMaterial = UUID.randomUUID();

        when(formulaRepo.findByParentProductId(product))
                .thenReturn(List.of(formula(product, intermediate, "2")));
        when(formulaRepo.findByParentProductId(intermediate))
                .thenReturn(List.of(formula(intermediate, rawMaterial, "5")));
        when(stockRepository.findAvailableByProductWarehouse(intermediate, WAREHOUSE_ID))
                .thenReturn(List.of(stock(intermediate, "4")));

        var result = exploder.explode(product, WAREHOUSE_ID, new BigDecimal("3"));

        // Intermediate has own stock -> consumed as-is, raw material is NOT exploded.
        assertThat(result).containsExactly(
                new BomExploder.ExplodedComponent(intermediate, new BigDecimal("6"), 0));
    }

    @Test
    void explode_manufacturableIntermediateWithoutStock_recursesToRawMaterial() {
        var product = UUID.randomUUID();
        var intermediate = UUID.randomUUID();
        var rawMaterial = UUID.randomUUID();

        when(formulaRepo.findByParentProductId(product))
                .thenReturn(List.of(formula(product, intermediate, "2")));
        when(formulaRepo.findByParentProductId(intermediate))
                .thenReturn(List.of(formula(intermediate, rawMaterial, "5")));
        when(formulaRepo.findByParentProductId(rawMaterial)).thenReturn(List.of());
        when(stockRepository.findAvailableByProductWarehouse(intermediate, WAREHOUSE_ID))
                .thenReturn(List.of());

        var result = exploder.explode(product, WAREHOUSE_ID, new BigDecimal("3"));

        // intermediate needed = 6 -> raw needed = 5 * 6 = 30, depth 1
        assertThat(result).containsExactly(
                new BomExploder.ExplodedComponent(rawMaterial, new BigDecimal("30"), 1));
    }

    @Test
    void explode_manufacturableIntermediateWithZeroAvailableStock_recurses() {
        var product = UUID.randomUUID();
        var intermediate = UUID.randomUUID();
        var rawMaterial = UUID.randomUUID();

        when(formulaRepo.findByParentProductId(product))
                .thenReturn(List.of(formula(product, intermediate, "1")));
        when(formulaRepo.findByParentProductId(intermediate))
                .thenReturn(List.of(formula(intermediate, rawMaterial, "2")));
        when(formulaRepo.findByParentProductId(rawMaterial)).thenReturn(List.of());
        // availableQuantity == 0 must NOT count as stock.
        when(stockRepository.findAvailableByProductWarehouse(intermediate, WAREHOUSE_ID))
                .thenReturn(List.of(stock(intermediate, "0")));

        var result = exploder.explode(product, WAREHOUSE_ID, BigDecimal.ONE);

        assertThat(result).containsExactly(
                new BomExploder.ExplodedComponent(rawMaterial, new BigDecimal("2"), 1));
    }

    @Test
    void explode_productWithoutFormula_throwsIllegalArgument() {
        var product = UUID.randomUUID();

        when(formulaRepo.findByParentProductId(product)).thenReturn(List.of());

        assertThatThrownBy(() -> exploder.explode(product, WAREHOUSE_ID, BigDecimal.ONE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no tiene fórmula");
    }

    @Test
    void explode_exceedingMaxDepth_throwsIllegalState() {
        // Chain of manufactured products, all without available stock:
        // P0 -> P1 -> ... -> P6 -> (P7). Depth 6 exceeds MAX_DEPTH (5).
        var ids = new UUID[8];
        for (int i = 0; i < ids.length; i++) {
            ids[i] = UUID.randomUUID();
        }
        for (int i = 0; i <= 6; i++) {
            when(formulaRepo.findByParentProductId(ids[i]))
                    .thenReturn(List.of(formula(ids[i], ids[i + 1], "1")));
        }
        when(stockRepository.findAvailableByProductWarehouse(any(), eq(WAREHOUSE_ID)))
                .thenReturn(List.of());

        assertThatThrownBy(() -> exploder.explode(ids[0], WAREHOUSE_ID, BigDecimal.ONE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Profundidad máxima");
    }

    @Test
    void wouldCreateCycle_detectsIndirectCycle() {
        var formulaId = UUID.randomUUID();
        var newComponentId = UUID.randomUUID();

        // newComponentId already has formulaId as a parent -> adding it would cycle.
        when(formulaRepo.findAllByComponentProductId(newComponentId))
                .thenReturn(List.of(formula(formulaId, newComponentId, "1")));

        assertThat(exploder.wouldCreateCycle(formulaId, newComponentId)).isTrue();
    }

    @Test
    void wouldCreateCycle_allowsAcyclicComponent() {
        var formulaId = UUID.randomUUID();
        var newComponentId = UUID.randomUUID();

        when(formulaRepo.findAllByComponentProductId(newComponentId)).thenReturn(List.of());

        assertThat(exploder.wouldCreateCycle(formulaId, newComponentId)).isFalse();
    }

    private ProductFormula formula(UUID parent, UUID component, String quantity) {
        return new ProductFormula(
                UUID.randomUUID(), parent, component, new BigDecimal(quantity),
                null, 1, null, true, OffsetDateTime.now(), null);
    }

    private InventoryStock stock(UUID productId, String available) {
        return new InventoryStock(
                UUID.randomUUID(), productId, UUID.randomUUID(), WAREHOUSE_ID,
                new BigDecimal(available), BigDecimal.ZERO, BigDecimal.TEN,
                OffsetDateTime.now(), null);
    }
}
