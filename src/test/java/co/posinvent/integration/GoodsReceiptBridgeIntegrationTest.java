package co.posinvent.integration;

import co.posinvent.application.dto.GoodsReceiptRequest;
import co.posinvent.application.dto.SupplierInvoiceRequest;
import co.posinvent.application.usecase.CreateGoodsReceiptUseCase;
import co.posinvent.application.usecase.SupplierInvoiceUseCase;
import co.posinvent.domain.model.PurchaseLineItem;
import co.posinvent.domain.model.PurchaseOrder;
import co.posinvent.domain.model.PurchaseOrderStatus;
import co.posinvent.domain.model.ThirdParty;
import co.posinvent.domain.model.Warehouse;
import co.posinvent.domain.repository.PurchaseOrderRepository;
import co.posinvent.domain.repository.PucAccountRepository;
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
 * Integration guard for the goods-receipt/inventory bridge (Contabilidad Fase 2, WU-36b, C6).
 *
 * <p>The inventory must be recognised exactly once — at physical control (the receipt). A receipt
 * posts debit inventario {@code 1435} / credit cuenta puente {@code 220510}, and the supplier
 * invoice clears the bridge (debit {@code 220510}) instead of debiting inventory again.</p>
 *
 * <p>Deliberately NOT {@code @Transactional}: fixtures are committed through a
 * {@link TransactionTemplate} so the use cases commit their own transactions and the
 * {@code AFTER_COMMIT} accounting listener runs exactly like in production.</p>
 */
class GoodsReceiptBridgeIntegrationTest extends AbstractIntegrationTest {

    // Received 10 units at a real unit cost of 100 → entry cost 10 × 100 = 1,000.
    private static final BigDecimal RECEIVED_QTY = new BigDecimal("10");
    private static final BigDecimal ACTUAL_UNIT_COST = new BigDecimal("100.00");
    private static final BigDecimal ENTRY_COST = new BigDecimal("1000.00");

    @Autowired private CreateGoodsReceiptUseCase createGoodsReceiptUseCase;
    @Autowired private SupplierInvoiceUseCase supplierInvoiceUseCase;
    @Autowired private PurchaseOrderRepository purchaseOrderRepository;
    @Autowired private PucAccountRepository pucRepo;

    private UUID supplierId;
    private UUID warehouseId;
    private UUID productId;
    private UUID ocId;

    @BeforeEach
    void setUp() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            warehouseId = TestDataFactory.createWarehouse(
                    em, "GR WU36B", Warehouse.WarehouseType.CORTES);
            productId = TestDataFactory.createProduct(
                    em, "GR36B", "Producto recepción WU36b", false, true);

            var supplier = new ThirdPartyEntity();
            supplier.setNumIdentification("SUP-GR-" + UUID.randomUUID().toString().substring(0, 8));
            supplier.setName("Supplier Recepción WU36b");
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
                    "OC-WU36B-" + UUID.randomUUID().toString().substring(0, 6),
                    null, null, null, null, null, null, "COP",
                    TEST_USER_ID, null, null, null, List.of(line)));
            ocId = oc.id();
        });
    }

    @Test
    void receiptRecognisesInventoryAndInvoiceClearsTheBridgeWithoutDoubleCounting() {
        // ── C6: the receipt recognises inventory at physical control. ──
        var receipt = createGoodsReceiptUseCase.process(new GoodsReceiptRequest(
                ocId,
                List.of(new GoodsReceiptRequest.ReceiptLine(
                        productId, warehouseId, RECEIVED_QTY, ACTUAL_UNIT_COST, null))),
                TEST_USER_ID);

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            var inventoryId = pucRepo.findByCode("1435").orElseThrow().id();
            var bridgeId = pucRepo.findByCode("220510").orElseThrow().id();

            var receiptEntries = entriesFor("INVENTORY", receipt.id());
            assertThat(receiptEntries).hasSize(1);
            var entry = receiptEntries.get(0);

            assertThat(totalDebit(entry)).isEqualByComparingTo(totalCredit(entry));
            assertThat(lineOn(entry, inventoryId).getDebit()).isEqualByComparingTo(ENTRY_COST);
            assertThat(lineOn(entry, bridgeId).getCredit()).isEqualByComparingTo(ENTRY_COST);
        });

        // ── C6 → invoice: the invoice clears the bridge, never touches inventory. ──
        var invoice = supplierInvoiceUseCase.create(new SupplierInvoiceRequest(
                supplierId, "FAC-WU36B-" + UUID.randomUUID().toString().substring(0, 6),
                LocalDate.now(), LocalDate.now().plusDays(30),
                ENTRY_COST, BigDecimal.ZERO, BigDecimal.ZERO, ENTRY_COST,
                null, List.of(ocId)), TEST_USER_ID);

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            var inventoryId = pucRepo.findByCode("1435").orElseThrow().id();
            var bridgeId = pucRepo.findByCode("220510").orElseThrow().id();
            var payableId = pucRepo.findByCode("2205").orElseThrow().id();

            var purchaseEntries = entriesFor("PURCHASE", invoice.id());
            assertThat(purchaseEntries).hasSize(1);
            var entry = purchaseEntries.get(0);

            assertThat(totalDebit(entry)).isEqualByComparingTo(totalCredit(entry));
            assertThat(lineOn(entry, bridgeId).getDebit()).isEqualByComparingTo(ENTRY_COST);
            assertThat(lineOn(entry, payableId).getCredit()).isEqualByComparingTo(ENTRY_COST);

            // The invoice must NOT debit inventory again.
            assertThat(entry.getLines())
                    .noneMatch(l -> inventoryId.equals(l.getAccountId()));

            // Inventory is recognised exactly once (the receipt), never by the invoice.
            assertThat(totalDebitOn(inventoryId, "INVENTORY", receipt.id()))
                    .isEqualByComparingTo(ENTRY_COST);
            assertThat(totalDebitOn(inventoryId, "PURCHASE", invoice.id()))
                    .isEqualByComparingTo(BigDecimal.ZERO);
        });
    }

    @Test
    void invoiceWithoutPriorReceiptStaysBalancedAgainstTheBridge() {
        var invoice = supplierInvoiceUseCase.create(new SupplierInvoiceRequest(
                supplierId, "FAC-NO-REC-" + UUID.randomUUID().toString().substring(0, 6),
                LocalDate.now(), LocalDate.now().plusDays(30),
                new BigDecimal("500.00"), BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("500.00"),
                null, List.of()), TEST_USER_ID);

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            var bridgeId = pucRepo.findByCode("220510").orElseThrow().id();
            var payableId = pucRepo.findByCode("2205").orElseThrow().id();

            var entries = entriesFor("PURCHASE", invoice.id());
            assertThat(entries).hasSize(1);
            var entry = entries.get(0);

            assertThat(totalDebit(entry)).isEqualByComparingTo(totalCredit(entry));
            assertThat(lineOn(entry, bridgeId).getDebit()).isEqualByComparingTo("500.00");
            assertThat(lineOn(entry, payableId).getCredit()).isEqualByComparingTo("500.00");
        });
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private List<JournalEntryEntity> entriesFor(String sourceType, UUID sourceId) {
        return em.createQuery(
                        "select e from JournalEntryEntity e "
                                + "where e.sourceType = :sourceType and e.sourceId = :sourceId",
                        JournalEntryEntity.class)
                .setParameter("sourceType", sourceType)
                .setParameter("sourceId", sourceId)
                .getResultList();
    }

    private BigDecimal totalDebitOn(UUID accountId, String sourceType, UUID sourceId) {
        return entriesFor(sourceType, sourceId).stream()
                .flatMap(e -> e.getLines().stream())
                .filter(l -> accountId.equals(l.getAccountId()))
                .map(JournalEntryLineEntity::getDebit)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static JournalEntryLineEntity lineOn(JournalEntryEntity entry, UUID accountId) {
        return entry.getLines().stream()
                .filter(l -> accountId.equals(l.getAccountId()))
                .findFirst().orElseThrow();
    }

    private static BigDecimal totalDebit(JournalEntryEntity entry) {
        return entry.getLines().stream()
                .map(JournalEntryLineEntity::getDebit)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal totalCredit(JournalEntryEntity entry) {
        return entry.getLines().stream()
                .map(JournalEntryLineEntity::getCredit)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
