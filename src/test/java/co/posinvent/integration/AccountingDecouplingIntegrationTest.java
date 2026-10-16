package co.posinvent.integration;

import co.posinvent.application.usecase.CheckoutRequest;
import co.posinvent.application.usecase.CreateJournalEntryUseCase;
import co.posinvent.application.usecase.PosCheckoutUseCase;
import co.posinvent.domain.model.Batch;
import co.posinvent.domain.model.JournalEntry;
import co.posinvent.domain.model.JournalEntryLine;
import co.posinvent.domain.model.SalesDocumentStatus;
import co.posinvent.domain.model.SalesDocumentType;
import co.posinvent.domain.model.Warehouse;
import co.posinvent.domain.repository.PucAccountRepository;
import co.posinvent.domain.repository.ThirdPartyRepository;
import co.posinvent.infrastructure.adapters.out.persistence.SalesDocumentEntity;
import co.posinvent.infrastructure.adapters.out.persistence.TestDataFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Integration guard for the accounting decoupling (Contabilidad Fase 2, WU-34).
 *
 * <p>The journal listener now runs {@code AFTER_COMMIT} in its own transaction, so a broken
 * PUC/accounting configuration can no longer roll back a sale that has already been charged.
 * This test proves both sides: a sale survives a broken accounting configuration (no broken
 * entry is posted), and with a correct PUC the entry is still posted after the sale commits.</p>
 *
 * <p>It also guards the WU-33 idempotency contract at the database level: the partial unique
 * index guarantees a single automatic entry per {@code (source_type, source_id)}.</p>
 */
class AccountingDecouplingIntegrationTest extends AbstractIntegrationTest {

    private static final BigDecimal UNIT_COST = new BigDecimal("3000.00");
    private static final BigDecimal SALE_PRICE = new BigDecimal("10000.00");
    private static final BigDecimal QTY = new BigDecimal("2");
    private static final BigDecimal SALE_TOTAL = new BigDecimal("20000.00");

    @Autowired private PosCheckoutUseCase checkoutUseCase;
    @Autowired private CreateJournalEntryUseCase journalUseCase;
    @Autowired private ThirdPartyRepository thirdPartyRepo;
    @Autowired private PucAccountRepository pucRepo;

    @Test
    void saleCommitsEvenWhenAccountingConfigurationIsBroken() {
        // A product whose own accounting template only declares the COGS debit. The resolved
        // journal would be unbalanced (receivable/income from the default template + COGS debit
        // with no inventory credit), i.e. an invalid entry. Before the fix the synchronous
        // listener threw inside the checkout transaction and rolled the whole sale back.
        UUID[] ids = seedProductWithBrokenTemplate();

        assertThatCode(() -> checkoutUseCase.checkout(
                new CheckoutRequest(ids[1], null,
                        List.of(new CheckoutRequest.PaymentLine("CASH", SALE_TOTAL))),
                TEST_USER_ID))
                .doesNotThrowAnyException();

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            var invoice = findInvoiceByOrder(ids[1]);
            assertThat(invoice).isNotNull();

            // The business side effects survive: the order advanced to INVOICED...
            var orderStatus = em.createQuery(
                            "select d.status from SalesDocumentEntity d where d.id = :id",
                            SalesDocumentStatus.class)
                    .setParameter("id", ids[1])
                    .getSingleResult();
            assertThat(orderStatus).isEqualTo(SalesDocumentStatus.INVOICED);

            // ...and no broken journal entry was posted for the invoice.
            var entryCount = em.createQuery(
                            "select count(e) from JournalEntryEntity e "
                                    + "where e.sourceType = 'SALE' and e.sourceId = :id",
                            Long.class)
                    .setParameter("id", invoice.getId())
                    .getSingleResult();
            assertThat(entryCount).isZero();
        });
    }

    @Test
    void journalEntryIsPostedAfterTheSaleCommits() {
        UUID orderId = seedStandardProductAndOrder();

        checkoutUseCase.checkout(
                new CheckoutRequest(orderId, null,
                        List.of(new CheckoutRequest.PaymentLine("CASH", SALE_TOTAL))),
                TEST_USER_ID);

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            var invoice = findInvoiceByOrder(orderId);

            var entry = em.createQuery(
                            "select e from JournalEntryEntity e "
                                    + "where e.sourceType = 'SALE' and e.sourceId = :id",
                            co.posinvent.infrastructure.adapters.out.persistence.JournalEntryEntity.class)
                    .setParameter("id", invoice.getId())
                    .getSingleResult();

            // The entry is posted for the committed invoice and stays balanced.
            var debit = entry.getLines().stream()
                    .map(l -> l.getDebit()).reduce(BigDecimal.ZERO, BigDecimal::add);
            var credit = entry.getLines().stream()
                    .map(l -> l.getCredit()).reduce(BigDecimal.ZERO, BigDecimal::add);
            assertThat(debit).isEqualByComparingTo(credit);
        });
    }

    @Test
    void createAutoIsIdempotentPerSourceAtTheDatabaseLevel() {
        UUID sourceId = UUID.randomUUID();
        var receivable = pucRepo.findByCode("1305").orElseThrow().id();
        var income = pucRepo.findByCode("4135").orElseThrow().id();
        var lines = List.of(
                new JournalEntryLine(null, null, receivable, new BigDecimal("100.00"),
                        BigDecimal.ZERO, "debit"),
                new JournalEntryLine(null, null, income, BigDecimal.ZERO,
                        new BigDecimal("100.00"), "credit"));

        JournalEntry first = journalUseCase.createAuto("SALE", sourceId, LocalDate.now(), "idem-1", lines);
        JournalEntry second = journalUseCase.createAuto("SALE", sourceId, LocalDate.now(), "idem-2", lines);

        assertThat(second.id()).isEqualTo(first.id());

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            var count = em.createQuery(
                            "select count(e) from JournalEntryEntity e "
                                    + "where e.sourceType = 'SALE' and e.sourceId = :id",
                            Long.class)
                    .setParameter("id", sourceId)
                    .getSingleResult();
            assertThat(count).isEqualTo(1L);
        });
    }

    // ── fixtures ────────────────────────────────────────────────────────────

    private SalesDocumentEntity findInvoiceByOrder(UUID orderId) {
        return em.createQuery(
                        "select d from SalesDocumentEntity d "
                                + "where d.type = :type and d.sourceDocumentId = :orderId",
                        SalesDocumentEntity.class)
                .setParameter("type", SalesDocumentType.INVOICE)
                .setParameter("orderId", orderId)
                .getSingleResult();
    }

    /** Seeds a product/order whose accounting resolves to a deliberately unbalanced entry. */
    private UUID[] seedProductWithBrokenTemplate() {
        UUID[] ids = new UUID[2];
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            var warehouseId = TestDataFactory.createWarehouse(
                    em, "DECOUPLE WU34", Warehouse.WarehouseType.CORTES);

            var productId = TestDataFactory.createProduct(
                    em, "TEST-BROKEN", "Broken Accounting Product", false, true);
            em.flush();
            TestDataFactory.setSalePriceAndTaxType(em, productId, SALE_PRICE, "EXENTO");

            var clientId = TestDataFactory.createClientThirdParty(
                    em, "BROKEN-" + UUID.randomUUID().toString().substring(0, 8), "Broken Client");

            var systemSupplier = thirdPartyRepo.findByNumIdentification("000000000-0").orElseThrow();
            var batchId = TestDataFactory.createBatch(
                    em, productId, systemSupplier.id(), warehouseId,
                    new BigDecimal("100"), Batch.BatchStatus.OPEN);
            TestDataFactory.createStock(
                    em, productId, batchId, warehouseId, new BigDecimal("100"), UNIT_COST);

            TestDataFactory.createActiveDianResolution(
                    em, "BRK", 1L, 100_000L,
                    LocalDate.now().minusDays(1), LocalDate.now().plusDays(1));

            attachBrokenProductTemplate(productId);

            var orderId = TestDataFactory.createConfirmedOrder(
                    em, "ORD-BRK-" + UUID.randomUUID().toString().substring(0, 6),
                    clientId, warehouseId, productId, batchId, QTY);
            em.flush();

            ids[0] = productId;
            ids[1] = orderId;
        });
        return ids;
    }

    /**
     * Links a product to a custom SALE template that declares only the COGS debit. The resulting
     * journal would be unbalanced, so the listener must skip it instead of aborting the sale.
     */
    private void attachBrokenProductTemplate(UUID productId) {
        UUID templateId = UUID.randomUUID();
        UUID cogsAccountId = pucRepo.findByCode("6135").orElseThrow().id();

        em.createNativeQuery(
                        "INSERT INTO accounting_templates (id, code, name, module, is_default, is_active) "
                                + "VALUES (CAST(:id AS uuid), :code, 'Broken', 'SALE', false, true)")
                .setParameter("id", templateId.toString())
                .setParameter("code", "BROKEN_" + templateId.toString().substring(0, 8))
                .executeUpdate();

        em.createNativeQuery(
                        "INSERT INTO accounting_template_entries "
                                + "(id, template_id, event_type, account_id, is_debit, priority) "
                                + "VALUES (CAST(:id AS uuid), CAST(:tid AS uuid), 'SALE_COGS', "
                                + "CAST(:acct AS uuid), true, 1)")
                .setParameter("id", UUID.randomUUID().toString())
                .setParameter("tid", templateId.toString())
                .setParameter("acct", cogsAccountId.toString())
                .executeUpdate();

        em.createNativeQuery(
                        "UPDATE products SET accounting_template_id = CAST(:tid AS uuid) "
                                + "WHERE id = CAST(:pid AS uuid)")
                .setParameter("tid", templateId.toString())
                .setParameter("pid", productId.toString())
                .executeUpdate();
    }

    /** Seeds a product/order with the default (correct) accounting configuration. */
    private UUID seedStandardProductAndOrder() {
        UUID[] ids = new UUID[1];
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            var warehouseId = TestDataFactory.createWarehouse(
                    em, "DECOUPLE WU34 OK", Warehouse.WarehouseType.CORTES);

            var productId = TestDataFactory.createProduct(
                    em, "TEST-DECOUPLE", "Decouple Product", false, true);
            em.flush();
            TestDataFactory.setSalePriceAndTaxType(em, productId, SALE_PRICE, "EXENTO");

            var clientId = TestDataFactory.createClientThirdParty(
                    em, "DECOUPLE-" + UUID.randomUUID().toString().substring(0, 8), "Decouple Client");

            var systemSupplier = thirdPartyRepo.findByNumIdentification("000000000-0").orElseThrow();
            var batchId = TestDataFactory.createBatch(
                    em, productId, systemSupplier.id(), warehouseId,
                    new BigDecimal("100"), Batch.BatchStatus.OPEN);
            TestDataFactory.createStock(
                    em, productId, batchId, warehouseId, new BigDecimal("100"), UNIT_COST);

            TestDataFactory.createActiveDianResolution(
                    em, "DCP", 1L, 100_000L,
                    LocalDate.now().minusDays(1), LocalDate.now().plusDays(1));

            ids[0] = TestDataFactory.createConfirmedOrder(
                    em, "ORD-DCP-" + UUID.randomUUID().toString().substring(0, 6),
                    clientId, warehouseId, productId, batchId, QTY);
            em.flush();
        });
        return ids[0];
    }
}
