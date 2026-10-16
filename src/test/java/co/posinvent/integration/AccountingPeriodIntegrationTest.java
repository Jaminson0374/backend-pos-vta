package co.posinvent.integration;

import co.posinvent.application.usecase.AccountingPeriodUseCase;
import co.posinvent.application.usecase.CheckoutRequest;
import co.posinvent.application.usecase.CreateJournalEntryUseCase;
import co.posinvent.application.usecase.PosCheckoutUseCase;
import co.posinvent.domain.exception.BusinessException;
import co.posinvent.domain.model.Batch;
import co.posinvent.domain.model.JournalEntryLine;
import co.posinvent.domain.model.SalesDocumentStatus;
import co.posinvent.domain.model.SalesDocumentType;
import co.posinvent.domain.model.Warehouse;
import co.posinvent.domain.repository.PucAccountRepository;
import co.posinvent.domain.repository.ThirdPartyRepository;
import co.posinvent.infrastructure.adapters.out.persistence.JournalEntryEntity;
import co.posinvent.infrastructure.adapters.out.persistence.SalesDocumentEntity;
import co.posinvent.infrastructure.adapters.out.persistence.TestDataFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration guard for accounting periods and journal author (Contabilidad Fase 2, A5 + A7).
 *
 * <p>A5: a sale whose document date falls in a CLOSED period still commits (the listener runs
 * {@code AFTER_COMMIT}, WU-34) but no journal entry is posted. With an open period (absent row)
 * the entry is posted dated with the document, not with "today".</p>
 *
 * <p>A7: the posted entry records the authenticated user in {@code created_by}.</p>
 */
class AccountingPeriodIntegrationTest extends AbstractIntegrationTest {

    private static final BigDecimal UNIT_COST = new BigDecimal("3000.00");
    private static final BigDecimal SALE_PRICE = new BigDecimal("10000.00");
    private static final BigDecimal QTY = new BigDecimal("2");
    private static final BigDecimal SALE_TOTAL = new BigDecimal("20000.00");
    private static final String CURRENT_PERIOD = YearMonth.now().toString();
    private static final String PAST_PERIOD = "2020-01";
    private static final String DIAN_PREFIX = "P";

    @Autowired private PosCheckoutUseCase checkoutUseCase;
    @Autowired private CreateJournalEntryUseCase journalUseCase;
    @Autowired private AccountingPeriodUseCase accountingPeriodUseCase;
    @Autowired private ThirdPartyRepository thirdPartyRepo;
    @Autowired private PucAccountRepository pucRepo;

    private UUID orderId;

    @BeforeEach
    void setUp() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            var warehouseId = TestDataFactory.createWarehouse(
                    em, "PERIOD WU35", Warehouse.WarehouseType.CORTES);

            var productId = TestDataFactory.createProduct(
                    em, "TEST-PERIOD", "Period Product", false, true);
            em.flush();
            TestDataFactory.setSalePriceAndTaxType(em, productId, SALE_PRICE, "EXENTO");

            var clientId = TestDataFactory.createClientThirdParty(
                    em, "PERIOD-" + UUID.randomUUID().toString().substring(0, 8), "Period Client");

            var systemSupplier = thirdPartyRepo.findByNumIdentification("000000000-0").orElseThrow();
            var batchId = TestDataFactory.createBatch(
                    em, productId, systemSupplier.id(), warehouseId,
                    new BigDecimal("100"), Batch.BatchStatus.OPEN);
            TestDataFactory.createStock(
                    em, productId, batchId, warehouseId, new BigDecimal("100"), UNIT_COST);

            TestDataFactory.createActiveDianResolution(
                    em, DIAN_PREFIX + UUID.randomUUID().toString().substring(0, 4).toUpperCase(), 1L, 1_000_000L,
                    LocalDate.now().minusDays(1), LocalDate.now().plusDays(1));

            orderId = TestDataFactory.createConfirmedOrder(
                    em, "ORD-PER-" + UUID.randomUUID().toString().substring(0, 6),
                    clientId, warehouseId, productId, batchId, QTY);
            em.flush();
        });
    }

    @AfterEach
    void removeCreatedPeriods() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                em.createNativeQuery("DELETE FROM accounting_periods WHERE period IN (:current, :past)")
                        .setParameter("current", CURRENT_PERIOD)
                        .setParameter("past", PAST_PERIOD)
                        .executeUpdate());
    }

    @Test
    void closedPeriodBlocksTheJournalEntryButNotTheSale() {
        accountingPeriodUseCase.close(CURRENT_PERIOD, TEST_USER_ID);

        checkoutUseCase.checkout(
                new CheckoutRequest(orderId, null,
                        List.of(new CheckoutRequest.PaymentLine("CASH", SALE_TOTAL))),
                TEST_USER_ID);

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            var invoice = findInvoiceByOrder(orderId);
            assertThat(invoice).isNotNull();

            // The sale survived: the order advanced even though accounting could not post (WU-34).
            var orderStatus = em.createQuery(
                            "select d.status from SalesDocumentEntity d where d.id = :id",
                            SalesDocumentStatus.class)
                    .setParameter("id", orderId)
                    .getSingleResult();
            assertThat(orderStatus).isEqualTo(SalesDocumentStatus.INVOICED);

            // ...and the SALE entry was blocked by the closed period.
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
    void openPeriodPostsEntryDatedWithDocumentAndAuthoredByTheUser() {
        checkoutUseCase.checkout(
                new CheckoutRequest(orderId, null,
                        List.of(new CheckoutRequest.PaymentLine("CASH", SALE_TOTAL))),
                TEST_USER_ID);

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            var invoice = findInvoiceByOrder(orderId);

            var entry = em.createQuery(
                            "select e from JournalEntryEntity e "
                                    + "where e.sourceType = 'SALE' and e.sourceId = :id",
                            JournalEntryEntity.class)
                    .setParameter("id", invoice.getId())
                    .getSingleResult();

            // A5: the accounting date is the document date, not "today".
            assertThat(entry.getEntryDate()).isEqualTo(invoice.getCreatedAt().toLocalDate());
            // A7: the author of the entry is the authenticated user.
            assertThat(entry.getCreatedBy()).isEqualTo(TEST_USER_ID);
        });
    }

    @Test
    void createAutoRejectsAnEntryInAnExplicitlyClosedPeriod() {
        accountingPeriodUseCase.close(PAST_PERIOD, TEST_USER_ID);

        var receivable = pucRepo.findByCode("1305").orElseThrow().id();
        var income = pucRepo.findByCode("4135").orElseThrow().id();
        var lines = List.of(
                new JournalEntryLine(null, null, receivable, new BigDecimal("100.00"),
                        BigDecimal.ZERO, "debit"),
                new JournalEntryLine(null, null, income, BigDecimal.ZERO,
                        new BigDecimal("100.00"), "credit"));

        assertThatThrownBy(() -> journalUseCase.createAuto(
                "SALE", UUID.randomUUID(), LocalDate.of(2020, 1, 15), "closed period", lines))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo("ACCOUNTING_PERIOD_CLOSED");
    }

    private SalesDocumentEntity findInvoiceByOrder(UUID orderId) {
        return em.createQuery(
                        "select d from SalesDocumentEntity d "
                                + "where d.type = :type and d.sourceDocumentId = :orderId",
                        SalesDocumentEntity.class)
                .setParameter("type", SalesDocumentType.INVOICE)
                .setParameter("orderId", orderId)
                .getSingleResult();
    }
}
