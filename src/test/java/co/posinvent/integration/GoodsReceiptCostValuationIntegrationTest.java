package co.posinvent.integration;

import co.posinvent.application.dto.GoodsReceiptRequest;
import co.posinvent.application.usecase.CreateGoodsReceiptUseCase;
import co.posinvent.domain.model.Batch;
import co.posinvent.domain.model.MovementType;
import co.posinvent.domain.model.PurchaseLineItem;
import co.posinvent.domain.model.PurchaseOrder;
import co.posinvent.domain.model.PurchaseOrderStatus;
import co.posinvent.domain.model.ThirdParty;
import co.posinvent.domain.model.Warehouse;
import co.posinvent.domain.repository.BatchRepository;
import co.posinvent.domain.repository.KardexRepository;
import co.posinvent.domain.repository.PucAccountRepository;
import co.posinvent.domain.repository.PurchaseOrderRepository;
import co.posinvent.domain.repository.StockRepository;
import co.posinvent.infrastructure.adapters.out.persistence.JournalEntryEntity;
import co.posinvent.infrastructure.adapters.out.persistence.JournalEntryLineEntity;
import co.posinvent.infrastructure.adapters.out.persistence.TestDataFactory;
import co.posinvent.infrastructure.adapters.out.persistence.ThirdPartyEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression guard for receipt cost valuation (bug de costo de recepción, Opción 1).
 *
 * <p>Convention in this system: {@code batches.purchase_cost} is the LINE TOTAL and any
 * unit cost is derived as {@code purchaseCost / initialWeight} (see
 * {@code ManualDesposteUseCase} / {@code ProcessSlaughterUseCase}). The frontend sends
 * {@code actualCost} as the UNIT cost ({@code ocLine.unitCost}).</p>
 *
 * <p>The receipt must therefore persist:
 * <ul>
 *   <li>{@code batch.purchase_cost = receivedQty × actualCost} (total),</li>
 *   <li>{@code stock.unit_cost = actualCost} (unit, no division),</li>
 *   <li>{@code kardex ENTRY.unit_cost = actualCost} (unit),</li>
 * </ul>
 * while the C6 journal entry stays balanced at {@code totalCost = receivedQty × actualCost}.</p>
 */
class GoodsReceiptCostValuationIntegrationTest extends AbstractIntegrationTest {

    private static final BigDecimal RECEIVED_QTY = new BigDecimal("10");
    private static final BigDecimal ACTUAL_UNIT_COST = new BigDecimal("100.00");
    private static final BigDecimal EXPECTED_LINE_TOTAL = new BigDecimal("1000.00"); // 10 × 100.00

    @Autowired private CreateGoodsReceiptUseCase createGoodsReceiptUseCase;
    @Autowired private PurchaseOrderRepository purchaseOrderRepository;
    @Autowired private StockRepository stockRepository;
    @Autowired private BatchRepository batchRepository;
    @Autowired private KardexRepository kardexRepository;
    @Autowired private PucAccountRepository pucRepo;

    private UUID supplierId;
    private UUID warehouseId;
    private UUID productId;
    private UUID ocId;

    @BeforeEach
    void setUp() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            warehouseId = TestDataFactory.createWarehouse(
                    em, "GR COST", Warehouse.WarehouseType.CORTES);
            productId = TestDataFactory.createProduct(
                    em, "GRCOST", "Producto recepción costo", false, true);

            var supplier = new ThirdPartyEntity();
            supplier.setNumIdentification("SUP-COST-" + UUID.randomUUID().toString().substring(0, 8));
            supplier.setName("Supplier Recepción Costo");
            supplier.setType(ThirdParty.ThirdPartyType.SUPPLIER);
            supplier.setPersonType(ThirdParty.PersonType.JURIDICA);
            supplier.setTaxRegime(ThirdParty.TaxRegime.ORDINARIO);
            supplier.setTaxResponsibilities(List.of());
            supplier.setActive(true);
            supplier.setCreditLimit(BigDecimal.ZERO);
            supplier.setCurrentBalance(BigDecimal.ZERO);
            em.persist(supplier);
            supplierId = supplier.getId();

            var line = new PurchaseLineItem(
                    null, null, productId, warehouseId,
                    RECEIVED_QTY, BigDecimal.ZERO, ACTUAL_UNIT_COST,
                    BigDecimal.ZERO, "EXENTO", 1);
            var oc = purchaseOrderRepository.save(new PurchaseOrder(
                    null, supplierId, PurchaseOrderStatus.PENDING, LocalDate.now(),
                    "OC-COST-" + UUID.randomUUID().toString().substring(0, 6),
                    null, null, null, null, null, null, "COP",
                    TEST_USER_ID, null, null, null, List.of(line)));
            ocId = oc.id();
        });
    }

    @Test
    void receiptPersistsUnitCostAsActualCostAndBatchTotalAsQtyTimesCost() {
        var receipt = createGoodsReceiptUseCase.process(new GoodsReceiptRequest(
                ocId,
                List.of(new GoodsReceiptRequest.ReceiptLine(
                        productId, warehouseId, RECEIVED_QTY, ACTUAL_UNIT_COST, null))),
                TEST_USER_ID);

        assertThat(receipt.batchIds()).hasSize(1);
        var batchId = receipt.batchIds().get(0);

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            // stock.unit_cost must be the UNIT cost (actualCost), not actualCost / receivedQty.
            var stock = stockRepository.findByProductBatchWarehouse(
                    productId, batchId, warehouseId).orElseThrow();
            assertThat(stock.unitCost()).isEqualByComparingTo(ACTUAL_UNIT_COST);

            // batch.purchase_cost must be the LINE TOTAL (receivedQty × actualCost).
            Batch batch = batchRepository.findById(batchId).orElseThrow();
            assertThat(batch.purchaseCost()).isEqualByComparingTo(EXPECTED_LINE_TOTAL);

            // The receipt's Kardex ENTRY movement must carry the UNIT cost.
            var movements = kardexRepository.findByReference("GOODS_RECEIPT", receipt.id());
            var entry = movements.stream()
                    .filter(m -> m.movementType() == MovementType.ENTRY)
                    .findFirst().orElseThrow();
            assertThat(entry.unitCost()).isEqualByComparingTo(ACTUAL_UNIT_COST);
        });
    }

    @Test
    void c6JournalEntryStaysBalancedAndValuesInventoryAtLineTotal() {
        var receipt = createGoodsReceiptUseCase.process(new GoodsReceiptRequest(
                ocId,
                List.of(new GoodsReceiptRequest.ReceiptLine(
                        productId, warehouseId, RECEIVED_QTY, ACTUAL_UNIT_COST, null))),
                TEST_USER_ID);

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            var inventoryId = pucRepo.findByCode("1435").orElseThrow().id();
            var bridgeId = pucRepo.findByCode("220510").orElseThrow().id();

            var entries = em.createQuery(
                            "select e from JournalEntryEntity e "
                                    + "where e.sourceType = 'INVENTORY' and e.sourceId = :id",
                            JournalEntryEntity.class)
                    .setParameter("id", receipt.id())
                    .getResultList();
            assertThat(entries).hasSize(1);
            var entry = entries.get(0);

            var totalDebit = entry.getLines().stream()
                    .map(JournalEntryLineEntity::getDebit)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            var totalCredit = entry.getLines().stream()
                    .map(JournalEntryLineEntity::getCredit)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            assertThat(totalDebit).isEqualByComparingTo(totalCredit);
            assertThat(lineOn(entry, inventoryId).getDebit()).isEqualByComparingTo(EXPECTED_LINE_TOTAL);
            assertThat(lineOn(entry, bridgeId).getCredit()).isEqualByComparingTo(EXPECTED_LINE_TOTAL);
        });
    }

    private static JournalEntryLineEntity lineOn(JournalEntryEntity entry, UUID accountId) {
        return entry.getLines().stream()
                .filter(l -> accountId.equals(l.getAccountId()))
                .findFirst().orElseThrow();
    }
}
