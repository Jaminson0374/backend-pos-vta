package co.posinvent.integration;

import co.posinvent.application.dto.ProduceRequest;
import co.posinvent.application.usecase.FormulaProductionUseCase;
import co.posinvent.domain.exception.BusinessException;
import co.posinvent.domain.model.Batch;
import co.posinvent.domain.model.Warehouse;
import co.posinvent.domain.repository.*;
import co.posinvent.domain.service.FefoPicker;
import co.posinvent.infrastructure.adapters.out.persistence.TestDataFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Transactional
class FormulaProductionIntegrationTest extends AbstractIntegrationTest {

    @Autowired private FormulaProductionUseCase useCase;
    @Autowired private FefoPicker fefoPicker;
    @Autowired private StockRepository stockRepo;
    @Autowired private BatchRepository batchRepo;
    @Autowired private ProductRepository productRepo;
    @Autowired private ProductFormulaRepository formulaRepo;
    @Autowired private ThirdPartyRepository thirdPartyRepo;
    @Autowired private WarehouseRepository warehouseRepo;

    private UUID warehouseId;
    private UUID rawMaterialId;
    private UUID rawBatchId;

    @BeforeEach
    void setUp() {
        warehouseId = TestDataFactory.createWarehouse(em, "CORTES Test", Warehouse.WarehouseType.CORTES);
        rawMaterialId = TestDataFactory.createProduct(em, "TEST-MP", "Canal de Cerdo", false, true);
        em.flush();

        var systemSupplier = thirdPartyRepo.findByNumIdentification("000000000-0");
        assertThat(systemSupplier).isPresent();

        rawBatchId = TestDataFactory.createBatch(em, systemSupplier.get().id(),
                warehouseId, new BigDecimal("50"), Batch.BatchStatus.OPEN);
        TestDataFactory.createStock(em, rawMaterialId, rawBatchId, warehouseId,
                new BigDecimal("50"), new BigDecimal("10"));
        em.flush();
    }

    @Test
    void shouldCreateBatchAndInventoryStockForProductionOutput() {
        var finishedId = TestDataFactory.createProduct(
                em, "TEST-FIN", "Lomo de Cerdo Despostado", true, true);
        TestDataFactory.createFormula(em, finishedId, rawMaterialId, new BigDecimal("2.0"));
        em.flush();

        var response = useCase.produce(
                new ProduceRequest(finishedId, warehouseId, new BigDecimal("10"),
                        BigDecimal.ZERO, null, "Test integración producción", null),
                TEST_USER_ID);

        assertThat(response.productionBatchId()).isNotNull();
        assertThat(response.inventoryBatchId()).isNotNull();
        assertThat(response.productName()).isEqualTo("Lomo de Cerdo Despostado");
        assertThat(response.quantityProduced()).isEqualByComparingTo(new BigDecimal("10"));

        var batch = batchRepo.findById(response.inventoryBatchId());
        assertThat(batch).isPresent();
        assertThat(batch.get().warehouseId()).isEqualTo(warehouseId);
        assertThat(batch.get().status()).isEqualTo(Batch.BatchStatus.OPEN);
        assertThat(batch.get().initialWeight()).isEqualByComparingTo(new BigDecimal("10"));

        var systemSupplier = thirdPartyRepo.findByNumIdentification("000000000-0");
        assertThat(systemSupplier).isPresent();
        assertThat(batch.get().supplierId()).isEqualTo(systemSupplier.get().id());

        var stocks = stockRepo.findByProduct(finishedId);
        assertThat(stocks).hasSize(1);
        assertThat(stocks.get(0).batchId()).isEqualTo(response.inventoryBatchId());
        assertThat(stocks.get(0).currentQuantity()).isEqualByComparingTo(new BigDecimal("10"));

        var allocations = fefoPicker.pick(finishedId, warehouseId, new BigDecimal("5"));
        assertThat(allocations).hasSize(1);
        assertThat(allocations.get(0).batchId()).isEqualTo(response.inventoryBatchId());
        assertThat(allocations.get(0).quantity()).isEqualByComparingTo(new BigDecimal("5"));
    }

    @Test
    void shouldEnableSecondaryProductionChainWhereFefoPickerFindsPriorStock() {
        var productAId = TestDataFactory.createProduct(
                em, "TEST-A", "Corte Primario", true, true);
        TestDataFactory.createFormula(em, productAId, rawMaterialId, new BigDecimal("2.0"));
        em.flush();

        var responseA = useCase.produce(
                new ProduceRequest(productAId, warehouseId, new BigDecimal("10"),
                        BigDecimal.ZERO, null, "Producción primaria", null),
                TEST_USER_ID);

        assertThat(stockRepo.findByProduct(productAId)).hasSize(1);

        var productBId = TestDataFactory.createProduct(
                em, "TEST-B", "Producto Derivado", true, true);
        TestDataFactory.createFormula(em, productBId, productAId, new BigDecimal("1.0"));
        em.flush();

        var responseB = useCase.produce(
                new ProduceRequest(productBId, warehouseId, new BigDecimal("5"),
                        BigDecimal.ZERO, null, "Producción secundaria", null),
                TEST_USER_ID);

        assertThat(responseB.inventoryBatchId()).isNotNull();
        assertThat(stockRepo.findByProduct(productBId)).hasSize(1);

        var stockAAfter = stockRepo.findByProductBatchWarehouse(
                productAId, responseA.inventoryBatchId(), warehouseId);
        assertThat(stockAAfter).isPresent();
        assertThat(stockAAfter.get().currentQuantity())
                .isEqualByComparingTo(new BigDecimal("5"));

        var allocationsB = fefoPicker.pick(productBId, warehouseId, new BigDecimal("3"));
        assertThat(allocationsB).hasSize(1);
    }

    @Test
    void shouldThrowInsufficientStockWhenFefoPickerCannotFulfill() {
        var finishedId = TestDataFactory.createProduct(
                em, "TEST-STK", "Producto Grandes Requisitos", true, true);
        TestDataFactory.createFormula(em, finishedId, rawMaterialId, new BigDecimal("2.0"));
        em.flush();

        var request = new ProduceRequest(
                finishedId, warehouseId, new BigDecimal("100"),
                BigDecimal.ZERO, null, "Debería fallar por stock", null);

        assertThatThrownBy(() -> useCase.produce(request, TEST_USER_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", "INSUFFICIENT_STOCK");
    }

    @Test
    void shouldThrowWhenProductIsNotManufacturable() {
        var notManufacturableId = TestDataFactory.createProduct(
                em, "TEST-NO", "Producto No Fabricado", false, true);
        em.flush();

        var request = new ProduceRequest(
                notManufacturableId, warehouseId, new BigDecimal("5"),
                BigDecimal.ZERO, null, "Debería fallar", null);

        assertThatThrownBy(() -> useCase.produce(request, TEST_USER_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("fabricado internamente");
    }

    @Test
    void shouldSetExpirationDateOnPerishableProductBatch() {
        var perishableId = TestDataFactory.createProduct(
                em, "TEST-PER", "Salchicha Fresca", true, true);
        TestDataFactory.makePerishable(em, perishableId);
        TestDataFactory.createFormula(em, perishableId, rawMaterialId, new BigDecimal("1.0"));
        em.flush();

        var expiration = LocalDate.of(2026, 8, 15);
        var request = new ProduceRequest(
                perishableId, warehouseId, new BigDecimal("5"),
                BigDecimal.ZERO, null, "Producción perecedero", expiration);

        var response = useCase.produce(request, TEST_USER_ID);

        var batch = batchRepo.findById(response.inventoryBatchId());
        assertThat(batch).isPresent();
        assertThat(batch.get().expirationDate()).isEqualTo(expiration);
    }

    @Test
    void shouldNotPickStockFromClosedBatch() {
        var finishedId = TestDataFactory.createProduct(
                em, "TEST-CLD", "Producto con Batch Cerrado", true, true);
        TestDataFactory.createFormula(em, finishedId, rawMaterialId, new BigDecimal("1.0"));
        em.flush();

        var response = useCase.produce(
                new ProduceRequest(finishedId, warehouseId, new BigDecimal("5"),
                        BigDecimal.ZERO, null, "Producción test", null),
                TEST_USER_ID);

        TestDataFactory.closeBatch(em, response.inventoryBatchId());
        em.flush();

        assertThatThrownBy(() -> fefoPicker.pick(finishedId, warehouseId, new BigDecimal("1")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", "NO_STOCK_AVAILABLE");
    }

    @Test
    void shouldPropagateOperatorIdToInventoryBatch() {
        var finishedId = TestDataFactory.createProduct(
                em, "TEST-OP", "Producto Operador", true, true);
        TestDataFactory.createFormula(em, finishedId, rawMaterialId, new BigDecimal("1.0"));
        em.flush();

        var response = useCase.produce(
                new ProduceRequest(finishedId, warehouseId, new BigDecimal("3"),
                        BigDecimal.ZERO, null, "Test operador", null),
                TEST_USER_ID);

        var batch = batchRepo.findById(response.inventoryBatchId());
        assertThat(batch).isPresent();
        assertThat(batch.get().createdBy()).isEqualTo(TEST_USER_ID);
    }
}
