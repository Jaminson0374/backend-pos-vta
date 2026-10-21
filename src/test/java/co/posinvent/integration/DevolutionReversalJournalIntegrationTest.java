package co.posinvent.integration;

import co.posinvent.application.dto.DevolutionRequest;
import co.posinvent.application.usecase.CheckoutRequest;
import co.posinvent.application.usecase.PosCheckoutUseCase;
import co.posinvent.application.usecase.PosDevolutionUseCase;
import co.posinvent.domain.model.Batch;
import co.posinvent.domain.model.SalesDocumentType;
import co.posinvent.domain.model.Warehouse;
import co.posinvent.domain.repository.PucAccountRepository;
import co.posinvent.domain.repository.ThirdPartyRepository;
import co.posinvent.infrastructure.adapters.out.persistence.JournalEntryEntity;
import co.posinvent.infrastructure.adapters.out.persistence.JournalEntryLineEntity;
import co.posinvent.infrastructure.adapters.out.persistence.SaleItemEntity;
import co.posinvent.infrastructure.adapters.out.persistence.SalesDocumentEntity;
import co.posinvent.infrastructure.adapters.out.persistence.TestDataFactory;
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
 * Regression guard for the devolution accounting (Contabilidad Fase 2, hallazgo C1).
 *
 * <p>A devolution publishes an {@code InvoiceIssuedEvent} for the credit note, whose totals are
 * negative. The listener used to account it exactly like a sale, producing negative journal lines
 * that violated the {@code journal_entry_lines} CHECK ({@code debit >= 0}, {@code credit >= 0}) and
 * rolled the whole devolution back. The credit note must instead post a balanced reversal with
 * crossed natures: receivable credited, income debited, and the inventory pair swapped at the
 * returned Kardex cost.</p>
 */
class DevolutionReversalJournalIntegrationTest extends AbstractIntegrationTest {

    private static final BigDecimal UNIT_COST = new BigDecimal("3000.00");
    private static final BigDecimal SALE_PRICE = new BigDecimal("10000.00");
    private static final BigDecimal QTY = new BigDecimal("2");
    private static final BigDecimal SALE_TOTAL = new BigDecimal("20000.00");   // 2 × 10,000 (EXENTO)
    private static final BigDecimal EXPECTED_COST = new BigDecimal("6000.00"); // 2 × 3,000 (Kardex)

    @Autowired private PosCheckoutUseCase checkoutUseCase;
    @Autowired private PosDevolutionUseCase devolutionUseCase;
    @Autowired private ThirdPartyRepository thirdPartyRepo;
    @Autowired private PucAccountRepository pucRepo;

    private UUID orderId;
    private UUID productId;
    private UUID shiftId;

    @BeforeEach
    void setUp() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            var warehouseId = TestDataFactory.createWarehouse(
                    em, "REV WU32", Warehouse.WarehouseType.CORTES);

            productId = TestDataFactory.createProduct(
                    em, "TEST-REV", "Reversal Product", false, true);
            em.flush();
            TestDataFactory.setSalePriceAndTaxType(em, productId, SALE_PRICE, "EXENTO");

            var clientId = TestDataFactory.createClientThirdParty(
                    em, "REV-" + UUID.randomUUID().toString().substring(0, 8), "Reversal Client");

            var systemSupplier = thirdPartyRepo.findByNumIdentification("000000000-0").orElseThrow();
            var batchId = TestDataFactory.createBatch(
                    em, productId, systemSupplier.id(), warehouseId,
                    new BigDecimal("100"), Batch.BatchStatus.OPEN);
            TestDataFactory.createStock(
                    em, productId, batchId, warehouseId, new BigDecimal("100"), UNIT_COST);

            TestDataFactory.createActiveDianResolution(
                    em, "REV", 1L, 100_000L,
                    LocalDate.now().minusDays(1), LocalDate.now().plusDays(1));

            orderId = TestDataFactory.createConfirmedOrder(
                    em,
                    "ORD-REV-" + UUID.randomUUID().toString().substring(0, 6),
                    clientId, warehouseId, productId, batchId, QTY);

            var cashRegisterId = TestDataFactory.createCashRegister(
                    em, "REV-REG-" + UUID.randomUUID().toString().substring(0, 8));
            shiftId = TestDataFactory.createOpenShift(em, TEST_USER_ID, cashRegisterId);
            em.flush();
        });
    }

    @Test
    void devolutionPostsBalancedReversalWithCrossedNatures() {
        // 1. Sell the goods (this posts the SALE entry: debit receivable 20,000 / credit income 20,000,
        //    debit COGS 6,000 / credit inventory 6,000).
        checkoutUseCase.checkout(
                new CheckoutRequest(orderId, null, shiftId,
                        List.of(new CheckoutRequest.PaymentLine("CASH", SALE_TOTAL))));

        UUID invoiceId = new TransactionTemplate(transactionManager).execute(status -> em.createQuery(
                        "select d from SalesDocumentEntity d "
                                + "where d.type = :type and d.sourceDocumentId = :orderId",
                        SalesDocumentEntity.class)
                .setParameter("type", SalesDocumentType.INVOICE)
                .setParameter("orderId", orderId)
                .getSingleResult()
                .getId());

        // 2. Full devolution. Before the fix this threw inside the listener (negative journal lines)
        //    and rolled the devolution back.
        var response = devolutionUseCase.processDevolution(
                new DevolutionRequest(invoiceId,
                        List.of(new DevolutionRequest.DevolutionItem(productId, QTY)),
                        "Devolución total"),
                TEST_USER_ID);

        // 3. The credit note exists and carries the reversal, not the sale, accounting.
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            var creditNote = em.createQuery(
                            "select d from SalesDocumentEntity d "
                                    + "where d.type = :type and d.sourceDocumentId = :id",
                            SalesDocumentEntity.class)
                    .setParameter("type", SalesDocumentType.CREDIT_NOTE)
                    .setParameter("id", invoiceId)
                    .getSingleResult();

            assertThat(creditNote.getId()).isEqualTo(response.creditNoteId());
            assertThat(creditNote.getTotalAmount()).isEqualByComparingTo(SALE_TOTAL.negate());

            // Each credit-note line is persisted exactly ONCE, by the document cascade.
            // The old code also ran an explicit itemRepo.save per line, inserting every
            // line twice (document_id = credit note id). One returned line ⇒ one row.
            var creditNoteItemCount = em.createQuery(
                            "select count(i) from SaleItemEntity i where i.document.id = :id",
                            Long.class)
                    .setParameter("id", creditNote.getId())
                    .getSingleResult();
            assertThat(creditNoteItemCount).isEqualTo(1L);

            var receivableId = pucRepo.findByCode("1305").orElseThrow().id();
            var incomeId = pucRepo.findByCode("4135").orElseThrow().id();
            var cogsId = pucRepo.findByCode("6135").orElseThrow().id();
            var inventoryId = pucRepo.findByCode("1435").orElseThrow().id();

            var entry = em.createQuery(
                            "select e from JournalEntryEntity e "
                                    + "where e.sourceType = 'SALE' and e.sourceId = :id",
                            JournalEntryEntity.class)
                    .setParameter("id", creditNote.getId())
                    .getSingleResult();

            // Every line is single-sided, positive and non-zero (the CHECK the old code violated).
            assertThat(entry.getLines()).isNotEmpty().allSatisfy(line -> {
                assertThat(line.getDebit()).isGreaterThanOrEqualTo(BigDecimal.ZERO);
                assertThat(line.getCredit()).isGreaterThanOrEqualTo(BigDecimal.ZERO);
                assertThat(line.getDebit().add(line.getCredit())).isGreaterThan(BigDecimal.ZERO);
                assertThat(line.getDebit().signum() == 0 || line.getCredit().signum() == 0).isTrue();
            });

            // Natures crossed: receivable credited |total|, income debited |subtotal|.
            var receivable = lineOn(entry.getLines(), receivableId);
            assertThat(receivable.getCredit()).isEqualByComparingTo(SALE_TOTAL);
            assertThat(receivable.getDebit()).isEqualByComparingTo(BigDecimal.ZERO);

            var income = lineOn(entry.getLines(), incomeId);
            assertThat(income.getDebit()).isEqualByComparingTo(SALE_TOTAL);
            assertThat(income.getCredit()).isEqualByComparingTo(BigDecimal.ZERO);

            // Inventory pair swapped, valued at the returned Kardex cost (NOT the sale price).
            var cogs = lineOn(entry.getLines(), cogsId);
            assertThat(cogs.getCredit()).isEqualByComparingTo(EXPECTED_COST);
            assertThat(cogs.getDebit()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(cogs.getCredit()).isNotEqualByComparingTo(SALE_TOTAL);

            var inventory = lineOn(entry.getLines(), inventoryId);
            assertThat(inventory.getDebit()).isEqualByComparingTo(EXPECTED_COST);
            assertThat(inventory.getCredit()).isEqualByComparingTo(BigDecimal.ZERO);

            // The reversal entry is balanced.
            var debit = entry.getLines().stream()
                    .map(JournalEntryLineEntity::getDebit)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            var credit = entry.getLines().stream()
                    .map(JournalEntryLineEntity::getCredit)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            assertThat(debit).isEqualByComparingTo(credit);
        });
    }

    private static JournalEntryLineEntity lineOn(List<JournalEntryLineEntity> lines, UUID accountId) {
        return lines.stream()
                .filter(l -> accountId.equals(l.getAccountId()))
                .findFirst().orElseThrow();
    }
}
