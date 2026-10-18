package co.posinvent.integration;

import co.posinvent.application.dto.DevolutionRequest;
import co.posinvent.application.usecase.AccountsReceivableUseCase;
import co.posinvent.application.usecase.CheckoutRequest;
import co.posinvent.application.usecase.PosCheckoutUseCase;
import co.posinvent.application.usecase.PosDevolutionUseCase;
import co.posinvent.domain.model.Batch;
import co.posinvent.domain.model.SalesDocumentStatus;
import co.posinvent.domain.model.SalesDocumentType;
import co.posinvent.domain.model.Warehouse;
import co.posinvent.domain.repository.ThirdPartyRepository;
import co.posinvent.infrastructure.adapters.out.persistence.AccountsReceivableEntity;
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
 * Regression guard for the devolution/receivable invariant (work unit A4).
 *
 * <p>A devolution used to reduce only {@code outstanding} while leaving {@code totalAmount}
 * untouched. Any later receipt recalculates {@code outstanding = totalAmount - paidAmount}, so it
 * re-inflated the receivable and undid the return. A fully returned receivable also stayed
 * OPEN/PARTIAL because the status was derived from the old total.</p>
 */
class DevolutionReceivableInvariantIntegrationTest extends AbstractIntegrationTest {

    private static final BigDecimal UNIT_COST = new BigDecimal("3000.00");
    private static final BigDecimal SALE_PRICE = new BigDecimal("10000.00");
    private static final BigDecimal QTY = new BigDecimal("2");
    private static final BigDecimal SALE_TOTAL = new BigDecimal("20000.00"); // 2 × 10,000 (EXENTO)

    @Autowired private PosCheckoutUseCase checkoutUseCase;
    @Autowired private PosDevolutionUseCase devolutionUseCase;
    @Autowired private AccountsReceivableUseCase accountsReceivableUseCase;
    @Autowired private ThirdPartyRepository thirdPartyRepo;

    private UUID orderId;
    private UUID productId;
    private UUID shiftId;

    @BeforeEach
    void setUp() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            var warehouseId = TestDataFactory.createWarehouse(
                    em, "AR INV WU34 " + UUID.randomUUID().toString().substring(0, 8),
                    Warehouse.WarehouseType.CORTES);

            productId = TestDataFactory.createProduct(
                    em, "TEST-ARINV-" + UUID.randomUUID().toString().substring(0, 8),
                    "AR Invariant Product", false, true);
            em.flush();
            TestDataFactory.setSalePriceAndTaxType(em, productId, SALE_PRICE, "EXENTO");

            var clientId = TestDataFactory.createClientThirdParty(
                    em, "ARINV-" + UUID.randomUUID().toString().substring(0, 8), "AR Invariant Client");

            var systemSupplier = thirdPartyRepo.findByNumIdentification("000000000-0").orElseThrow();
            var batchId = TestDataFactory.createBatch(
                    em, productId, systemSupplier.id(), warehouseId,
                    new BigDecimal("100"), Batch.BatchStatus.OPEN);
            TestDataFactory.createStock(
                    em, productId, batchId, warehouseId, new BigDecimal("100"), UNIT_COST);

            // Each fixture re-seeds the active resolution, so the prefix must be unique per test:
            // otherwise both tests issue the same first invoice number and violate its unique index.
            TestDataFactory.createActiveDianResolution(
                    em, "A" + UUID.randomUUID().toString().substring(0, 8), 1L, 100_000L,
                    LocalDate.now().minusDays(1), LocalDate.now().plusDays(1));

            orderId = TestDataFactory.createConfirmedOrder(
                    em,
                    "ORD-ARINV-" + UUID.randomUUID().toString().substring(0, 6),
                    clientId, warehouseId, productId, batchId, QTY);

            var cashRegisterId = TestDataFactory.createCashRegister(
                    em, "ARINV-REG-" + UUID.randomUUID().toString().substring(0, 8));
            shiftId = TestDataFactory.createOpenShift(em, TEST_USER_ID, cashRegisterId);
            em.flush();
        });
    }

    @Test
    void partialDevolutionThenReceiptDoesNotReinflateReceivable() {
        var fixture = checkoutAndIssueCreditInvoice();

        // Return 1 of the 2 units: the receivable total itself must shrink to 10,000.
        devolutionUseCase.processDevolution(
                new DevolutionRequest(fixture.invoiceId(),
                        List.of(new DevolutionRequest.DevolutionItem(productId, BigDecimal.ONE)),
                        "Devolución parcial"),
                TEST_USER_ID);

        var afterDevolution = freshReceivable(fixture.arId());
        assertThat(afterDevolution.getTotalAmount()).isEqualByComparingTo("10000.00");
        assertThat(afterDevolution.getPaidAmount()).isEqualByComparingTo("0.00");
        assertThat(afterDevolution.getOutstanding()).isEqualByComparingTo("10000.00");
        assertThat(afterDevolution.getStatus()).isEqualTo("OPEN");

        // The AR application a receipt performs must not re-inflate the receivable.
        accountsReceivableUseCase.applyPayment(fixture.arId(), new BigDecimal("10000.00"));

        var afterPayment = freshReceivable(fixture.arId());
        assertThat(afterPayment.getTotalAmount()).isEqualByComparingTo("10000.00");
        assertThat(afterPayment.getPaidAmount()).isEqualByComparingTo("10000.00");
        assertThat(afterPayment.getOutstanding()).isEqualByComparingTo("0.00");
        assertThat(afterPayment.getStatus()).isEqualTo("PAID");
    }

    @Test
    void fullDevolutionLeavesReceivablePaidWithZeroOutstanding() {
        var fixture = checkoutAndIssueCreditInvoice();

        // Return both units: nothing is owed anymore, even though nothing was ever paid.
        devolutionUseCase.processDevolution(
                new DevolutionRequest(fixture.invoiceId(),
                        List.of(new DevolutionRequest.DevolutionItem(productId, QTY)),
                        "Devolución total"),
                TEST_USER_ID);

        var receivable = freshReceivable(fixture.arId());
        assertThat(receivable.getTotalAmount()).isEqualByComparingTo("0.00");
        assertThat(receivable.getPaidAmount()).isEqualByComparingTo("0.00");
        assertThat(receivable.getOutstanding()).isEqualByComparingTo("0.00");
        assertThat(receivable.getStatus()).isEqualTo("PAID");
    }

    /**
     * Sells the seeded order, then turns the resulting POS invoice into a credit-sale invoice and
     * seeds the receivable a credit sale would have created. The POS checkout always produces a
     * DRAFT cash invoice, so this is the smallest fixture that exercises the devolution AR branch.
     */
    private InvoiceFixture checkoutAndIssueCreditInvoice() {
        checkoutUseCase.checkout(
                new CheckoutRequest(orderId, null, shiftId,
                        List.of(new CheckoutRequest.PaymentLine("CASH", SALE_TOTAL))),
                TEST_USER_ID);

        return new TransactionTemplate(transactionManager).execute(status -> {
            var invoice = em.createQuery(
                            "select d from SalesDocumentEntity d "
                                    + "where d.type = :type and d.sourceDocumentId = :orderId",
                            SalesDocumentEntity.class)
                    .setParameter("type", SalesDocumentType.INVOICE)
                    .setParameter("orderId", orderId)
                    .getSingleResult();

            invoice.setStatus(SalesDocumentStatus.ISSUED);
            invoice.setCreditSale(true);

            var ar = new AccountsReceivableEntity();
            ar.setDocumentId(invoice.getId());
            ar.setClientId(invoice.getClientId());
            ar.setTotalAmount(SALE_TOTAL);
            ar.setPaidAmount(BigDecimal.ZERO);
            ar.setOutstanding(SALE_TOTAL);
            ar.setDueDate(LocalDate.now().plusDays(30));
            ar.setStatus("OPEN");
            em.persist(ar);
            em.flush();

            return new InvoiceFixture(invoice.getId(), ar.getId());
        });
    }

    /** Clears the persistence context first so assertions cannot pass on a cached entity. */
    private AccountsReceivableEntity freshReceivable(UUID arId) {
        return new TransactionTemplate(transactionManager).execute(status -> {
            em.clear();
            return em.find(AccountsReceivableEntity.class, arId);
        });
    }

    private record InvoiceFixture(UUID invoiceId, UUID arId) {
    }
}
