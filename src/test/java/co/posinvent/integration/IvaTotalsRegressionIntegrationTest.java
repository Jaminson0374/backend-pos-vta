package co.posinvent.integration;

import co.posinvent.application.usecase.CheckoutRequest;
import co.posinvent.application.usecase.PosCheckoutUseCase;
import co.posinvent.domain.model.Batch;
import co.posinvent.domain.model.SalesDocumentType;
import co.posinvent.domain.model.Warehouse;
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

/**
 * Regression guard for the retirement of the 8% IVA (Impoconsumo).
 *
 * <p>After removing {@code total_tax_8} from the schema and the 8% handling from the
 * domain (normative table is 0/5/19), this test proves the remaining rates still
 * compute exactly. It seeds an EXENTO product and an IVA_19 product with stock and a
 * single CONFIRMED order carrying one line per product, runs the POS checkout, and
 * asserts the resulting INVOICE totals are split and summed correctly.</p>
 */
class IvaTotalsRegressionIntegrationTest extends AbstractIntegrationTest {

    private static final BigDecimal EXENTO_PRICE = new BigDecimal("10000.00");
    private static final BigDecimal IVA19_PRICE = new BigDecimal("5000.00");
    private static final BigDecimal IVA19_QTY = new BigDecimal("2");
    private static final String DIAN_PREFIX = "IVA";

    @Autowired private PosCheckoutUseCase checkoutUseCase;
    @Autowired private ThirdPartyRepository thirdPartyRepo;

    private UUID orderId;
    private UUID shiftId;

    @BeforeEach
    void setUp() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            var warehouseId = TestDataFactory.createWarehouse(
                    em, "IVA REG", Warehouse.WarehouseType.CORTES);

            var exentoProductId = TestDataFactory.createProduct(
                    em, "TEST-IVA0", "Exento Product", false, true);
            var iva19ProductId = TestDataFactory.createProduct(
                    em, "TEST-IVA19", "Iva 19 Product", false, true);
            em.flush();

            TestDataFactory.setSalePriceAndTaxType(em, exentoProductId, EXENTO_PRICE, "EXENTO");
            TestDataFactory.setSalePriceAndTaxType(em, iva19ProductId, IVA19_PRICE, "IVA_19");

            var clientId = TestDataFactory.createClientThirdParty(
                    em, "IVA-" + UUID.randomUUID().toString().substring(0, 8), "Iva Totals Client");

            var systemSupplier = thirdPartyRepo.findByNumIdentification("000000000-0").orElseThrow();
            var exentoBatchId = TestDataFactory.createBatch(
                    em, exentoProductId, systemSupplier.id(), warehouseId,
                    new BigDecimal("100"), Batch.BatchStatus.OPEN);
            var iva19BatchId = TestDataFactory.createBatch(
                    em, iva19ProductId, systemSupplier.id(), warehouseId,
                    new BigDecimal("100"), Batch.BatchStatus.OPEN);
            TestDataFactory.createStock(
                    em, exentoProductId, exentoBatchId, warehouseId, new BigDecimal("100"), BigDecimal.ZERO);
            TestDataFactory.createStock(
                    em, iva19ProductId, iva19BatchId, warehouseId, new BigDecimal("100"), BigDecimal.ZERO);

            TestDataFactory.createActiveDianResolution(
                    em, DIAN_PREFIX, 1L, 100_000L,
                    LocalDate.now().minusDays(1), LocalDate.now().plusDays(1));

            orderId = TestDataFactory.createConfirmedOrder(
                    em,
                    "ORD-IVA-" + UUID.randomUUID().toString().substring(0, 6),
                    clientId, warehouseId,
                    List.of(
                            new TestDataFactory.OrderLine(exentoProductId, exentoBatchId, BigDecimal.ONE),
                            new TestDataFactory.OrderLine(iva19ProductId, iva19BatchId, IVA19_QTY)
                    ));

            var cashRegisterId = TestDataFactory.createCashRegister(
                    em, "IVA-REG-" + UUID.randomUUID().toString().substring(0, 8));
            shiftId = TestDataFactory.createOpenShift(em, TEST_USER_ID, cashRegisterId);
            em.flush();
        });
    }

    @Test
    void checkoutComputesZeroAndNineteenTaxesExactly() {
        var request = new CheckoutRequest(orderId, null, shiftId,
                List.of(new CheckoutRequest.PaymentLine("CASH", new BigDecimal("25000.00"))));

        checkoutUseCase.checkout(request, TEST_USER_ID);

        var tx = new TransactionTemplate(transactionManager);
        var invoice = tx.execute(s -> em.createQuery(
                        "select d from SalesDocumentEntity d "
                                + "where d.type = :type and d.sourceDocumentId = :orderId",
                        SalesDocumentEntity.class)
                .setParameter("type", SalesDocumentType.INVOICE)
                .setParameter("orderId", orderId)
                .getSingleResult());

        // EXENTO line: 1 x 10,000.00 -> subtotal 10,000.00, tax 0.00
        // IVA_19 line: 2 x  5,000.00 -> subtotal 10,000.00, tax 1,900.00
        assertThat(invoice.getTotalNet()).isEqualByComparingTo("20000.00");
        assertThat(invoice.getTotalTax0()).isEqualByComparingTo("0.00");
        assertThat(invoice.getTotalTax5()).isEqualByComparingTo("0.00");
        assertThat(invoice.getTotalTax19()).isEqualByComparingTo("1900.00");
        assertThat(invoice.getTotalAmount()).isEqualByComparingTo("21900.00");
    }
}
