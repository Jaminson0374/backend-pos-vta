package co.posinvent.integration;

import co.posinvent.application.usecase.CheckoutRequest;
import co.posinvent.application.usecase.ManageSalesDocumentUseCase;
import co.posinvent.application.usecase.PosCheckoutUseCase;
import co.posinvent.domain.exception.BusinessException;
import co.posinvent.domain.model.Batch;
import co.posinvent.domain.model.SalesDocumentStatus;
import co.posinvent.domain.model.SalesDocumentType;
import co.posinvent.domain.model.Warehouse;
import co.posinvent.domain.repository.AccountsReceivableRepository;
import co.posinvent.domain.repository.ThirdPartyRepository;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * End-to-end proof that the credit limit is validated against the outstanding derived from the
 * previously issued receivables (work unit A4, R3-derived-credit-proof).
 *
 * <p>The first invoice is issued as a credit sale and its AR row is written by
 * {@code AccountsReceivableUseCase.createFromInvoice()} — never hand-persisted. The second invoice
 * of the same client then exceeds the 15,000.00 limit (10,000.00 already outstanding + 10,000.00
 * new total), so issuing it must fail and leave no receivable and no status change behind.</p>
 *
 * <p>Deliberately NOT {@code @Transactional}: the fixtures commit their own transactions so the
 * use cases see the rows exactly as in production.</p>
 */
class CreditLimitDerivedOutstandingIntegrationTest extends AbstractIntegrationTest {

    private static final BigDecimal SALE_PRICE = new BigDecimal("5000.00");
    private static final BigDecimal UNIT_COST = new BigDecimal("3000.00");
    private static final BigDecimal QTY = new BigDecimal("2");
    private static final BigDecimal INVOICE_TOTAL = new BigDecimal("10000.00"); // 2 × 5,000 (EXENTO)
    private static final BigDecimal CREDIT_LIMIT = new BigDecimal("15000.00");

    @Autowired private PosCheckoutUseCase checkoutUseCase;
    @Autowired private ManageSalesDocumentUseCase manageSalesDocumentUseCase;
    @Autowired private AccountsReceivableRepository accountsReceivableRepository;
    @Autowired private ThirdPartyRepository thirdPartyRepository;

    private UUID order1Id;
    private UUID order2Id;

    @BeforeEach
    void setUp() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            var warehouseId = TestDataFactory.createWarehouse(
                    em, "CREDIT LIMIT WH " + UUID.randomUUID().toString().substring(0, 8),
                    Warehouse.WarehouseType.CORTES);

            var productId = TestDataFactory.createProduct(
                    em, "TEST-CRLIMIT-" + UUID.randomUUID().toString().substring(0, 8),
                    "Credit Limit Product", false, true);
            em.flush();
            TestDataFactory.setSalePriceAndTaxType(em, productId, SALE_PRICE, "EXENTO");

            var clientId = TestDataFactory.createClientThirdParty(
                    em,
                    "CRLIMIT-" + UUID.randomUUID().toString().substring(0, 8),
                    "Credit Limit Client",
                    CREDIT_LIMIT);

            var systemSupplier = thirdPartyRepository.findByNumIdentification("000000000-0").orElseThrow();
            var batchId = TestDataFactory.createBatch(
                    em, productId, systemSupplier.id(), warehouseId,
                    new BigDecimal("100"), Batch.BatchStatus.OPEN);
            TestDataFactory.createStock(
                    em, productId, batchId, warehouseId, new BigDecimal("100"), UNIT_COST);

            // Each fixture re-seeds the active resolution, so the prefix must be unique per test:
            // otherwise a later test issues the same first invoice number and violates its unique index.
            TestDataFactory.createActiveDianResolution(
                    em, "C" + UUID.randomUUID().toString().substring(0, 8), 1L, 100_000L,
                    LocalDate.now().minusDays(1), LocalDate.now().plusDays(1));

            order1Id = TestDataFactory.createConfirmedOrder(
                    em,
                    "ORD-CRLIMIT-1-" + UUID.randomUUID().toString().substring(0, 6),
                    clientId, warehouseId, productId, batchId, QTY);
            order2Id = TestDataFactory.createConfirmedOrder(
                    em,
                    "ORD-CRLIMIT-2-" + UUID.randomUUID().toString().substring(0, 6),
                    clientId, warehouseId, productId, batchId, QTY);
            em.flush();
        });
    }

    @Test
    void secondIssuedCreditInvoiceIsRejectedUsingTheArWrittenByTheFirst() {
        // ── First credit invoice: issue it and let the use case write the AR ──
        var invoice1 = checkoutDraftInvoice(order1Id);
        markCreditSale(invoice1);
        manageSalesDocumentUseCase.transitionDocument(invoice1, SalesDocumentStatus.ISSUED);

        var receivable = accountsReceivableRepository.findByDocumentId(invoice1).orElseThrow();
        assertThat(receivable.totalAmount()).isEqualByComparingTo(INVOICE_TOTAL);
        assertThat(receivable.outstanding()).isEqualByComparingTo(INVOICE_TOTAL);

        // ── Second credit invoice: the first AR already consumes most of the limit ──
        var invoice2 = checkoutDraftInvoice(order2Id);
        markCreditSale(invoice2);

        assertThatThrownBy(() ->
                manageSalesDocumentUseCase.transitionDocument(invoice2, SalesDocumentStatus.ISSUED))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo("CREDIT_LIMIT_EXCEEDED"));

        // The rejected issue must not leave a receivable nor move the invoice out of DRAFT.
        assertThat(accountsReceivableRepository.findByDocumentId(invoice2)).isEmpty();
        assertThat(freshInvoiceStatus(invoice2)).isEqualTo(SalesDocumentStatus.DRAFT);
    }

    /** Checks out the seeded order, returning the id of the DRAFT invoice it produced. */
    private UUID checkoutDraftInvoice(UUID orderId) {
        checkoutUseCase.checkout(
                new CheckoutRequest(orderId, null,
                        List.of(new CheckoutRequest.PaymentLine("CASH", INVOICE_TOTAL))),
                TEST_USER_ID);

        return new TransactionTemplate(transactionManager).execute(status -> em.createQuery(
                        "select d.id from SalesDocumentEntity d "
                                + "where d.type = :type and d.sourceDocumentId = :orderId",
                        UUID.class)
                .setParameter("type", SalesDocumentType.INVOICE)
                .setParameter("orderId", orderId)
                .getSingleResult());
    }

    /**
     * Turns the draft POS invoice into a credit sale. Only the flag is set: the status transition
     * and the AR creation remain the use case's responsibility.
     */
    private void markCreditSale(UUID invoiceId) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            var invoice = em.find(SalesDocumentEntity.class, invoiceId);
            invoice.setCreditSale(true);
            em.flush();
        });
    }

    /** Clears the persistence context first so assertions cannot pass on a cached entity. */
    private SalesDocumentStatus freshInvoiceStatus(UUID invoiceId) {
        return new TransactionTemplate(transactionManager).execute(status -> {
            em.clear();
            return em.find(SalesDocumentEntity.class, invoiceId).getStatus();
        });
    }
}
