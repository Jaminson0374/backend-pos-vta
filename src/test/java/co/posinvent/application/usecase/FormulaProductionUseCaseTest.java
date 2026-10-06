package co.posinvent.application.usecase;

import co.posinvent.application.dto.ProduceRequest;
import co.posinvent.domain.exception.BusinessException;
import co.posinvent.domain.model.*;
import co.posinvent.domain.repository.*;
import co.posinvent.domain.service.BomExploder;
import co.posinvent.domain.service.FefoPicker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FormulaProductionUseCaseTest {

    @Mock private ProductFormulaRepository formulaRepo;
    @Mock private KardexRepository kardexRepo;
    @Mock private RecordMovementUseCase recordMovementUseCase;
    @Mock private ProductionBatchRepository batchRepo;
    @Mock private CompanyConfigRepository configRepo;
    @Mock private ProductRepository productRepo;
    @Mock private BomExploder bomExploder;
    @Mock private FefoPicker fefoPicker;
    @Mock private StockRepository stockRepository;
    @Mock private CostingOrchestrator costingService;
    @Mock private ThirdPartyRepository thirdPartyRepo;
    @Mock private BatchRepository batchInventoryRepo;

    private FormulaProductionUseCase useCase;

    private static final UUID FORMULA_PRODUCT_ID = UUID.randomUUID();
    private static final UUID RAW_MATERIAL_ID = UUID.randomUUID();
    private static final UUID WAREHOUSE_ID = UUID.randomUUID();
    private static final UUID OPERATOR_ID = UUID.randomUUID();
    private static final UUID SYSTEM_SUPPLIER_ID = UUID.randomUUID();
    private static final UUID INVENTORY_BATCH_ID = UUID.randomUUID();
    private static final UUID PRODUCTION_BATCH_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        useCase = new FormulaProductionUseCase(
                formulaRepo, kardexRepo, recordMovementUseCase, batchRepo,
                configRepo, productRepo, bomExploder, fefoPicker,
                stockRepository, costingService, thirdPartyRepo, batchInventoryRepo
        );
    }

    @Test
    void shouldFailWithInsufficientStockWhenFefoPickerRejects() {
        var parentProduct = simpleProduct(FORMULA_PRODUCT_ID, "Producto Final", true);
        when(productRepo.findById(FORMULA_PRODUCT_ID)).thenReturn(Optional.of(parentProduct));

        var exploded = new BomExploder.ExplodedComponent(RAW_MATERIAL_ID, new BigDecimal("10"), 0);
        when(bomExploder.explode(FORMULA_PRODUCT_ID, new BigDecimal("5")))
                .thenReturn(List.of(exploded));

        var rawMaterial = simpleProduct(RAW_MATERIAL_ID, "Materia Prima", false);
        when(productRepo.findById(RAW_MATERIAL_ID)).thenReturn(Optional.of(rawMaterial));

        when(fefoPicker.pick(RAW_MATERIAL_ID, WAREHOUSE_ID, new BigDecimal("10")))
                .thenThrow(new BusinessException("INSUFFICIENT_STOCK",
                        "Stock insuficiente para el producto " + RAW_MATERIAL_ID));

        var request = new ProduceRequest(
                FORMULA_PRODUCT_ID, WAREHOUSE_ID,
                new BigDecimal("5"), BigDecimal.ZERO, null, "Test production", null
        );

        assertThatThrownBy(() -> useCase.produce(request, OPERATOR_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", "INSUFFICIENT_STOCK");

        verify(batchRepo, never()).save(any());
    }

    @Test
    void shouldRejectNonManufacturedProduct() {
        var parentProduct = simpleProduct(FORMULA_PRODUCT_ID, "Producto No Fabricado", false);
        when(productRepo.findById(FORMULA_PRODUCT_ID)).thenReturn(Optional.of(parentProduct));

        var request = new ProduceRequest(
                FORMULA_PRODUCT_ID, WAREHOUSE_ID,
                new BigDecimal("5"), BigDecimal.ZERO, null, "Test production", null
        );

        assertThatThrownBy(() -> useCase.produce(request, OPERATOR_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("fabricado internamente");

        verify(bomExploder, never()).explode(any(), any());
    }

    @Test
    void shouldThrowWhenSystemSupplierMissing() {
        var parentProduct = simpleProduct(FORMULA_PRODUCT_ID, "Producto Final", true);
        when(productRepo.findById(FORMULA_PRODUCT_ID)).thenReturn(Optional.of(parentProduct));

        var exploded = new BomExploder.ExplodedComponent(RAW_MATERIAL_ID, new BigDecimal("10"), 0);
        when(bomExploder.explode(FORMULA_PRODUCT_ID, new BigDecimal("5")))
                .thenReturn(List.of(exploded));

        var rawMaterial = simpleProduct(RAW_MATERIAL_ID, "Materia Prima", false);
        when(productRepo.findById(RAW_MATERIAL_ID)).thenReturn(Optional.of(rawMaterial));

        var allocations = List.of(new BatchAllocation(
                UUID.randomUUID(), new BigDecimal("10"), BigDecimal.TEN));
        when(fefoPicker.pick(RAW_MATERIAL_ID, WAREHOUSE_ID, new BigDecimal("10")))
                .thenReturn(allocations);

        when(kardexRepo.getUnitCost(RAW_MATERIAL_ID, WAREHOUSE_ID, "WEIGHTED_AVERAGE"))
                .thenReturn(Optional.of(BigDecimal.TEN));

        when(configRepo.findConfig()).thenReturn(Optional.empty());

        var savedBatch = prodBatch(PRODUCTION_BATCH_ID);
        when(batchRepo.save(any())).thenReturn(savedBatch);

        when(thirdPartyRepo.findByNumIdentification("000000000-0"))
                .thenReturn(Optional.empty());

        var request = new ProduceRequest(
                FORMULA_PRODUCT_ID, WAREHOUSE_ID,
                new BigDecimal("5"), BigDecimal.ZERO, null, "Test production", null
        );

        assertThatThrownBy(() -> useCase.produce(request, OPERATOR_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PRODUCCIÓN INTERNA");
    }

    @Test
    void shouldCreateBatchAndInventoryStockForProductionOutput() {
        setupHappyPath();

        var request = new ProduceRequest(
                FORMULA_PRODUCT_ID, WAREHOUSE_ID,
                new BigDecimal("5"), BigDecimal.ZERO, null, "Test production", null
        );

        var response = useCase.produce(request, OPERATOR_ID);

        // Verify inventory batch was created with correct supplier, warehouse, quantity
        ArgumentCaptor<Batch> batchCaptor = ArgumentCaptor.forClass(Batch.class);
        verify(batchInventoryRepo).save(batchCaptor.capture());
        Batch captured = batchCaptor.getValue();
        assertThat(captured.supplierId()).isEqualTo(SYSTEM_SUPPLIER_ID);
        assertThat(captured.warehouseId()).isEqualTo(WAREHOUSE_ID);
        assertThat(captured.initialWeight()).isEqualByComparingTo(new BigDecimal("5"));

        // Verify InventoryStock was created for output (first of two saves)
        ArgumentCaptor<InventoryStock> stockCaptor = ArgumentCaptor.forClass(InventoryStock.class);
        verify(stockRepository, times(2)).save(stockCaptor.capture());
        InventoryStock outputStock = stockCaptor.getAllValues().get(0);
        assertThat(outputStock.productId()).isEqualTo(FORMULA_PRODUCT_ID);
        assertThat(outputStock.batchId()).isEqualTo(INVENTORY_BATCH_ID);

        // Verify response contains both batch IDs
        assertThat(response.productionBatchId()).isEqualTo(PRODUCTION_BATCH_ID);
        assertThat(response.inventoryBatchId()).isEqualTo(INVENTORY_BATCH_ID);
    }

    @Test
    void shouldSetRealBatchIdOnProductionOutputMovement() {
        setupHappyPath();

        useCase.produce(new ProduceRequest(
                FORMULA_PRODUCT_ID, WAREHOUSE_ID, new BigDecimal("5"),
                BigDecimal.ZERO, null, "Test production", null), OPERATOR_ID);

        ArgumentCaptor<UUID> batchIdArg = ArgumentCaptor.forClass(UUID.class);
        verify(recordMovementUseCase, times(2)).record(any(), batchIdArg.capture(), any(),
                any(), any(), any(), any(), any(), any(), any(), any());

        // First capture = PRODUCTION_CONSUMPTION, second = PRODUCTION_OUTPUT (inventory batch)
        List<UUID> captured = batchIdArg.getAllValues();
        assertThat(captured.get(1)).isNotNull();
        assertThat(captured.get(1)).isEqualTo(INVENTORY_BATCH_ID);
    }

    @Test
    void shouldLinkProductionBatchToInventoryBatch() {
        setupHappyPath();

        useCase.produce(new ProduceRequest(
                FORMULA_PRODUCT_ID, WAREHOUSE_ID, new BigDecimal("5"),
                BigDecimal.ZERO, null, "Test production", null), OPERATOR_ID);

        ArgumentCaptor<ProductionBatch> batchCaptor = ArgumentCaptor.forClass(ProductionBatch.class);
        verify(batchRepo, times(2)).save(batchCaptor.capture());
        List<ProductionBatch> saved = batchCaptor.getAllValues();
        assertThat(saved.get(1).batchId()).isNotNull();
        assertThat(saved.get(1).batchId()).isEqualTo(INVENTORY_BATCH_ID);
    }

    @Test
    void shouldPropagateOperatorIdToBatch() {
        setupHappyPath();

        useCase.produce(new ProduceRequest(
                FORMULA_PRODUCT_ID, WAREHOUSE_ID, new BigDecimal("5"),
                BigDecimal.ZERO, null, "Test production", null), OPERATOR_ID);

        ArgumentCaptor<Batch> batchCaptor = ArgumentCaptor.forClass(Batch.class);
        verify(batchInventoryRepo).save(batchCaptor.capture());
        assertThat(batchCaptor.getValue().createdBy()).isEqualTo(OPERATOR_ID);
    }

    // ── Happy path setup ────────────────────────────────────────────────

    private void setupHappyPath() {
        var parentProduct = simpleProduct(FORMULA_PRODUCT_ID, "Prod Final", true);
        when(productRepo.findById(FORMULA_PRODUCT_ID)).thenReturn(Optional.of(parentProduct));

        when(bomExploder.explode(FORMULA_PRODUCT_ID, new BigDecimal("5")))
                .thenReturn(List.of(new BomExploder.ExplodedComponent(RAW_MATERIAL_ID, new BigDecimal("10"), 0)));

        var rawMaterial = simpleProduct(RAW_MATERIAL_ID, "MP", false);
        when(productRepo.findById(RAW_MATERIAL_ID)).thenReturn(Optional.of(rawMaterial));

        var allocBatchId = UUID.randomUUID();
        when(fefoPicker.pick(RAW_MATERIAL_ID, WAREHOUSE_ID, new BigDecimal("10")))
                .thenReturn(List.of(new BatchAllocation(allocBatchId, new BigDecimal("10"), BigDecimal.TEN)));
        when(kardexRepo.getUnitCost(RAW_MATERIAL_ID, WAREHOUSE_ID, "WEIGHTED_AVERAGE"))
                .thenReturn(Optional.of(BigDecimal.TEN));
        when(configRepo.findConfig()).thenReturn(Optional.empty());

        var pb = prodBatch(PRODUCTION_BATCH_ID);
        when(batchRepo.save(any())).thenReturn(pb);

        when(thirdPartyRepo.findByNumIdentification("000000000-0"))
                .thenReturn(Optional.of(minimalThirdParty()));

        var ib = new Batch(INVENTORY_BATCH_ID, UUID.randomUUID(), SYSTEM_SUPPLIER_ID, WAREHOUSE_ID,
                LocalDate.now(), new BigDecimal("5"), new BigDecimal("100"),
                Batch.BatchStatus.OPEN, "Prod lote " + PRODUCTION_BATCH_ID,
                null, OPERATOR_ID, null, null, null, null,
                null, null, null, null, null, null, null);
        when(batchInventoryRepo.save(any(Batch.class))).thenReturn(ib);

        when(stockRepository.save(any(InventoryStock.class))).thenAnswer(i -> i.getArgument(0));

        var upb = prodBatchWithInventory(PRODUCTION_BATCH_ID, INVENTORY_BATCH_ID);
        when(batchRepo.save(any(ProductionBatch.class))).thenReturn(pb, upb);

        var stock = new InventoryStock(
                UUID.randomUUID(), RAW_MATERIAL_ID, allocBatchId, WAREHOUSE_ID,
                new BigDecimal("20"), BigDecimal.ZERO, BigDecimal.TEN, OffsetDateTime.now(), null);
        when(stockRepository.findByProductBatchWarehouse(eq(RAW_MATERIAL_ID), eq(allocBatchId), eq(WAREHOUSE_ID)))
                .thenReturn(Optional.of(stock));

        var movement = new InventoryMovement(
                UUID.randomUUID(), RAW_MATERIAL_ID, allocBatchId, WAREHOUSE_ID,
                MovementType.PRODUCTION_CONSUMPTION, new BigDecimal("-10"), BigDecimal.TEN,
                new BigDecimal("20"), new BigDecimal("10"),
                "PRODUCTION", PRODUCTION_BATCH_ID,
                "Consumo producción #" + PRODUCTION_BATCH_ID, "SYSTEM", OffsetDateTime.now());
        when(recordMovementUseCase.record(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(movement);

        when(costingService.resolveCostOnExit(any(), any(), any(), any())).thenReturn(BigDecimal.TEN);
        when(costingService.resolveCostOnEntry(any(), any(), any(), any(), any(), any()))
                .thenReturn(new BigDecimal("20"));
        when(kardexRepo.getCurrentStock(FORMULA_PRODUCT_ID, WAREHOUSE_ID))
                .thenReturn(BigDecimal.ZERO, BigDecimal.ZERO);

        doNothing().when(productRepo).recalculateTotalStock(any());
    }

    // ── Helpers ─────────────────────────────────────────────────────────

    private Product simpleProduct(UUID id, String name, boolean manufacturedInHouse) {
        return new Product(
                id, "P-" + id.toString().substring(0, 8), name,
                null, null, null, null, null, null, null, null, null, null,
                BigDecimal.ZERO, BigDecimal.ZERO, "EXENTO", BigDecimal.TEN,
                "PROMEDIO_PONDERADO",
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.TEN, BigDecimal.ZERO,
                manufacturedInHouse, false, false, false, false, false, true,
                null, null, null, null, null, null, null,
                true, 0,
                OffsetDateTime.now().minusDays(2), OffsetDateTime.now().minusDays(1),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of()
        );
    }

    private ThirdParty minimalThirdParty() {
        return new ThirdParty(
                SYSTEM_SUPPLIER_ID, "000000000-0", "PRODUCCIÓN INTERNA",
                ThirdParty.ThirdPartyType.SUPPLIER, null,
                BigDecimal.ZERO, BigDecimal.ZERO,
                ThirdParty.PersonType.JURIDICA, ThirdParty.TaxRegime.ORDINARIO,
                List.of(), null, null, true,
                OffsetDateTime.now(), OffsetDateTime.now(),
                null, null, null, null, null, null, null, null,
                null, null, null, null, 0,
                null, null, null, null, null, null, null, null,
                false, false, false, false, false, null
        );
    }

    private ProductionBatch prodBatch(UUID batchId) {
        return new ProductionBatch(
                batchId, FORMULA_PRODUCT_ID,
                new BigDecimal("5"), new BigDecimal("5"),
                new BigDecimal("100"), BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("100"), new BigDecimal("20"),
                BigDecimal.ZERO, BigDecimal.ZERO, "Test", OPERATOR_ID, null, null
        );
    }

    private ProductionBatch prodBatchWithInventory(UUID batchId, UUID inventoryBatchId) {
        return new ProductionBatch(
                batchId, FORMULA_PRODUCT_ID,
                new BigDecimal("5"), new BigDecimal("5"),
                new BigDecimal("100"), BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("100"), new BigDecimal("20"),
                BigDecimal.ZERO, BigDecimal.ZERO, "Test", OPERATOR_ID, null, inventoryBatchId
        );
    }
}
