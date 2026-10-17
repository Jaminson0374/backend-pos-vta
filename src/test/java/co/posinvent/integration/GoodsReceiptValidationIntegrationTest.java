package co.posinvent.integration;

import co.posinvent.application.dto.GoodsReceiptRequest;
import co.posinvent.application.usecase.CreateGoodsReceiptUseCase;
import co.posinvent.domain.exception.BusinessException;
import co.posinvent.domain.model.PurchaseLineItem;
import co.posinvent.domain.model.PurchaseOrder;
import co.posinvent.domain.model.PurchaseOrderStatus;
import co.posinvent.domain.model.ThirdParty;
import co.posinvent.domain.model.Warehouse;
import co.posinvent.domain.repository.BatchRepository;
import co.posinvent.domain.repository.KardexRepository;
import co.posinvent.domain.repository.PurchaseOrderRepository;
import co.posinvent.domain.repository.StockRepository;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Compras WU-45b — regression guards for the goods-receipt audit findings M5 + M6.
 *
 * <ul>
 *   <li><b>M5a:</b> each receipt line mints a brand-new {@code Batch}, so the stock row for it can
 *       never pre-exist — a valid receipt must create exactly ONE distinct batch + stock row per
 *       line with the Kardex ENTRY starting at {@code previousQty = 0}.</li>
 *   <li><b>M5b:</b> a receipt line referencing an unknown warehouse/product must be rejected with
 *       {@code WAREHOUSE_NOT_FOUND} / {@code PRODUCT_NOT_FOUND} before any receipt, batch or stock
 *       row is written.</li>
 *   <li><b>M6:</b> duplicate {@code productId}s within one receipt must fail explicitly with
 *       {@code DUPLICATE_RECEIPT_LINE} instead of relying on the DB unique constraint at flush.</li>
 * </ul>
 *
 * <p>Deliberately NOT {@code @Transactional}: fixtures are committed through a
 * {@link TransactionTemplate} so the use case commits/rolls back its own transaction and assert
 * queries observe committed state exactly like production callers.</p>
 */
class GoodsReceiptValidationIntegrationTest extends AbstractIntegrationTest {

    private static final BigDecimal ORDERED_QTY = new BigDecimal("100");
    private static final BigDecimal RECEIVED_QTY = new BigDecimal("10");
    private static final BigDecimal ACTUAL_UNIT_COST = new BigDecimal("100.00");

    @Autowired private CreateGoodsReceiptUseCase createGoodsReceiptUseCase;
    @Autowired private PurchaseOrderRepository purchaseOrderRepository;
    @Autowired private StockRepository stockRepository;
    @Autowired private BatchRepository batchRepository;
    @Autowired private KardexRepository kardexRepository;

    private UUID supplierId;
    private UUID warehouseId;
    private UUID product1Id;
    private UUID product2Id;
    private UUID ocId;

    @BeforeEach
    void setUp() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            warehouseId = TestDataFactory.createWarehouse(
                    em, "GR WU45B", Warehouse.WarehouseType.CORTES);
            product1Id = TestDataFactory.createProduct(
                    em, "GR45B-A", "Producto recepción WU45b A", false, true);
            product2Id = TestDataFactory.createProduct(
                    em, "GR45B-B", "Producto recepción WU45b B", false, true);

            var supplier = new ThirdPartyEntity();
            supplier.setNumIdentification("SUP-WU45B-" + UUID.randomUUID().toString().substring(0, 8));
            supplier.setName("Supplier Recepción WU45b");
            supplier.setType(ThirdParty.ThirdPartyType.SUPPLIER);
            supplier.setPersonType(ThirdParty.PersonType.JURIDICA);
            supplier.setTaxRegime(ThirdParty.TaxRegime.ORDINARIO);
            supplier.setTaxResponsibilities(List.of());
            supplier.setActive(true);
            supplier.setCreditLimit(BigDecimal.ZERO);
            supplier.setCurrentBalance(BigDecimal.ZERO);
            em.persist(supplier);
            supplierId = supplier.getId();

            var line1 = new PurchaseLineItem(
                    null, null, product1Id, warehouseId,
                    ORDERED_QTY, BigDecimal.ZERO, ACTUAL_UNIT_COST,
                    BigDecimal.ZERO, "EXENTO", 1);
            var line2 = new PurchaseLineItem(
                    null, null, product2Id, warehouseId,
                    ORDERED_QTY, BigDecimal.ZERO, ACTUAL_UNIT_COST,
                    BigDecimal.ZERO, "EXENTO", 2);
            var oc = purchaseOrderRepository.save(new PurchaseOrder(
                    null, supplierId, PurchaseOrderStatus.PENDING, LocalDate.now(),
                    "OC-WU45B-" + UUID.randomUUID().toString().substring(0, 6),
                    null, null, null, null, null, null, "COP",
                    TEST_USER_ID, null, null, null, List.of(line1, line2)));
            ocId = oc.id();
        });
    }

    @Test
    void unknownWarehouseIsRejectedAndNothingIsPersisted() {
        var unknownWarehouse = UUID.randomUUID();

        assertThatThrownBy(() -> createGoodsReceiptUseCase.process(new GoodsReceiptRequest(
                ocId,
                List.of(new GoodsReceiptRequest.ReceiptLine(
                        product1Id, unknownWarehouse, RECEIVED_QTY, ACTUAL_UNIT_COST, null))),
                TEST_USER_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", "WAREHOUSE_NOT_FOUND");

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            assertThat(countReceipts(ocId)).isZero();
            assertThat(countStock(product1Id)).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(countBatches(product1Id)).isZero();
        });
    }

    @Test
    void unknownProductIsRejectedAndNothingIsPersisted() {
        var unknownProduct = UUID.randomUUID();

        assertThatThrownBy(() -> createGoodsReceiptUseCase.process(new GoodsReceiptRequest(
                ocId,
                List.of(new GoodsReceiptRequest.ReceiptLine(
                        unknownProduct, warehouseId, RECEIVED_QTY, ACTUAL_UNIT_COST, null))),
                TEST_USER_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", "PRODUCT_NOT_FOUND");

        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                assertThat(countReceipts(ocId)).isZero());
    }

    @Test
    void duplicateProductLineIsRejectedWithExplicitCode() {
        assertThatThrownBy(() -> createGoodsReceiptUseCase.process(new GoodsReceiptRequest(
                ocId,
                List.of(
                        new GoodsReceiptRequest.ReceiptLine(
                                product1Id, warehouseId, RECEIVED_QTY, ACTUAL_UNIT_COST, null),
                        new GoodsReceiptRequest.ReceiptLine(
                                product1Id, warehouseId, RECEIVED_QTY, ACTUAL_UNIT_COST, null))),
                TEST_USER_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", "DUPLICATE_RECEIPT_LINE");

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            assertThat(countReceipts(ocId)).isZero();
            assertThat(countBatches(product1Id)).isZero();
            assertThat(countStock(product1Id)).isEqualByComparingTo(BigDecimal.ZERO);
        });
    }

    @Test
    void validReceiptCreatesOneDistinctBatchAndStockPerLineWithZeroPreviousQty() {
        var receipt = createGoodsReceiptUseCase.process(new GoodsReceiptRequest(
                ocId,
                List.of(
                        new GoodsReceiptRequest.ReceiptLine(
                                product1Id, warehouseId, RECEIVED_QTY, ACTUAL_UNIT_COST, null),
                        new GoodsReceiptRequest.ReceiptLine(
                                product2Id, warehouseId, RECEIVED_QTY, ACTUAL_UNIT_COST, null))),
                TEST_USER_ID);

        assertThat(receipt.batchIds()).hasSize(2).doesNotHaveDuplicates();

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            assertThat(countReceipts(ocId)).isEqualTo(1);
            assertThat(countBatches(product1Id)).isEqualTo(1);
            assertThat(countBatches(product2Id)).isEqualTo(1);
            assertThat(countStock(product1Id)).isEqualByComparingTo(RECEIVED_QTY);
            assertThat(countStock(product2Id)).isEqualByComparingTo(RECEIVED_QTY);

            // A freshly minted lot starts the Kardex at previousQty = 0 (mirrors ProcessSlaughterUseCase).
            var movements = kardexRepository.findByReference("GOODS_RECEIPT", receipt.id());
            assertThat(movements).hasSize(2);
            assertThat(movements)
                    .allSatisfy(m -> assertThat(m.previousQty())
                            .isEqualByComparingTo(BigDecimal.ZERO));
        });
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private long countReceipts(UUID ocId) {
        return em.createQuery(
                        "select count(g) from GoodsReceiptEntity g where g.ocId = :ocId", Long.class)
                .setParameter("ocId", ocId)
                .getSingleResult();
    }

    private long countBatches(UUID productId) {
        return em.createQuery(
                        "select count(b) from BatchEntity b where b.productId = :productId", Long.class)
                .setParameter("productId", productId)
                .getSingleResult();
    }

    private BigDecimal countStock(UUID productId) {
        return em.createQuery(
                        "select coalesce(sum(s.currentQuantity), 0) from InventoryStockEntity s "
                                + "where s.productId = :productId", BigDecimal.class)
                .setParameter("productId", productId)
                .getSingleResult();
    }
}
