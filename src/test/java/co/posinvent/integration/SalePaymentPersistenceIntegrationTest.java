package co.posinvent.integration;

import co.posinvent.application.usecase.CheckoutRequest;
import co.posinvent.application.usecase.PosCheckoutUseCase;
import co.posinvent.domain.model.Batch;
import co.posinvent.domain.model.SalePayment;
import co.posinvent.domain.model.Warehouse;
import co.posinvent.domain.repository.SalePaymentRepository;
import co.posinvent.domain.repository.ThirdPartyRepository;
import co.posinvent.infrastructure.adapters.out.persistence.TestDataFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for POS checkout payment persistence (audit C3).
 *
 * <p>One CONFIRMED order is checked out with TWO tender lines (CASH 600 +
 * CARD 400 = 1000 total). Before the wiring, {@code CheckoutRequest.payments()}
 * was validated for {@code POS_UNDERPAID} and then discarded, so no row was
 * persisted. After the fix each line lands in {@code sale_payments}, linked to
 * the issued invoice, and their sum equals the invoice total.</p>
 *
 * <p>Deliberately NOT {@code @Transactional}: setup commits via a
 * {@link TransactionTemplate} so the checkout's own REQUIRES_NEW transaction
 * observes the committed rows and its committed payments are readable after
 * the call, exactly like production callers.</p>
 */
class SalePaymentPersistenceIntegrationTest extends AbstractIntegrationTest {

    private static final BigDecimal SALE_PRICE = new BigDecimal("1000.00");
    private static final BigDecimal STOCK_QUANTITY = new BigDecimal("100");

    @Autowired private PosCheckoutUseCase checkoutUseCase;
    @Autowired private SalePaymentRepository salePaymentRepo;
    @Autowired private ThirdPartyRepository thirdPartyRepo;

    private UUID warehouseId;
    private UUID clientId;
    private UUID orderId;

    @BeforeEach
    void setUp() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            warehouseId = TestDataFactory.createWarehouse(
                    em, "SALE PAY", Warehouse.WarehouseType.CORTES);
            var productId = TestDataFactory.createProduct(
                    em, "TEST-SP", "Sale Payment", false, true);
            em.flush();

            // Simple, tax-free pricing: total = salePrice * qty (EXENTO => 0% tax).
            TestDataFactory.setSalePriceAndTaxType(em, productId, SALE_PRICE, "EXENTO");

            clientId = TestDataFactory.createClientThirdParty(
                    em, "SP-" + UUID.randomUUID().toString().substring(0, 8), "Sale Payment Client");

            var systemSupplier = thirdPartyRepo.findByNumIdentification("000000000-0").orElseThrow();
            var batchId = TestDataFactory.createBatch(
                    em, productId, systemSupplier.id(), warehouseId,
                    STOCK_QUANTITY, Batch.BatchStatus.OPEN);
            TestDataFactory.createStock(
                    em, productId, batchId, warehouseId, STOCK_QUANTITY, BigDecimal.ZERO);

            // Checkout now requires an ACTIVE, valid DIAN resolution to number invoices.
            TestDataFactory.createActiveDianResolution(
                    em, "SP", 1L, 100000L, java.time.LocalDate.now().minusDays(1),
                    java.time.LocalDate.now().plusDays(1));

            orderId = TestDataFactory.createConfirmedOrder(
                    em, "ORD-SP-" + UUID.randomUUID().toString().substring(0, 6),
                    clientId, warehouseId, productId, batchId, BigDecimal.ONE);
            em.flush();
        });
    }

    @Test
    void persistsOneSalePaymentPerTenderLineLinkedToInvoice() {
        var request = new CheckoutRequest(
                orderId, null,
                List.of(
                        new CheckoutRequest.PaymentLine("CASH", new BigDecimal("600.00")),
                        new CheckoutRequest.PaymentLine("CARD", new BigDecimal("400.00"))));

        var result = checkoutUseCase.checkout(request, TEST_USER_ID);

        var payments = salePaymentRepo.findBySalesDocumentId(result.invoiceId());

        assertThat(payments).hasSize(2);
        assertThat(payments).extracting(SalePayment::method)
                .containsExactlyInAnyOrder("CASH", "CARD");
        assertThat(payments).allSatisfy(p ->
                assertThat(p.salesDocumentId()).isEqualTo(result.invoiceId()));

        var summed = payments.stream()
                .map(SalePayment::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(summed).isEqualByComparingTo("1000.00");
        assertThat(summed).isEqualByComparingTo(result.totalAmount());
    }
}
