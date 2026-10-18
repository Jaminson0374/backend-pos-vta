package co.posinvent.integration;

import co.posinvent.application.usecase.CheckoutRequest;
import co.posinvent.application.usecase.PosCheckoutUseCase;
import co.posinvent.domain.model.Batch;
import co.posinvent.domain.model.SalesDocumentType;
import co.posinvent.domain.model.Warehouse;
import co.posinvent.domain.repository.ThirdPartyRepository;
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
 * End-to-end proof that discounts are applied BEFORE IVA.
 *
 * <p>Seeds one EXENTO line (gross 10,000, 10% manual line discount) and one IVA_19
 * line (gross 10,000, 500 fixed manual line discount), then checks out with an
 * additional 10% document discount. Verifies the invoice totals split exactly, that
 * the document discount is prorated across lines and that the IVA_19 was computed on
 * the discounted base — not on the list price.</p>
 */
class DiscountTotalsIntegrationTest extends AbstractIntegrationTest {

    private static final BigDecimal EXENTO_PRICE = new BigDecimal("10000.00");
    private static final BigDecimal IVA19_PRICE = new BigDecimal("5000.00");
    private static final BigDecimal IVA19_QTY = new BigDecimal("2");
    private static final String DIAN_PREFIX = "DSC";

    @Autowired private PosCheckoutUseCase checkoutUseCase;
    @Autowired private ThirdPartyRepository thirdPartyRepo;

    private UUID orderId;
    private UUID exentoProductId;
    private UUID iva19ProductId;
    private UUID shiftId;

    @BeforeEach
    void setUp() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            var warehouseId = TestDataFactory.createWarehouse(
                    em, "DSC", Warehouse.WarehouseType.CORTES);

            exentoProductId = TestDataFactory.createProduct(
                    em, "TEST-DSC0", "Discount Exento Product", false, true);
            iva19ProductId = TestDataFactory.createProduct(
                    em, "TEST-DSC19", "Discount Iva 19 Product", false, true);
            em.flush();

            TestDataFactory.setSalePriceAndTaxType(em, exentoProductId, EXENTO_PRICE, "EXENTO");
            TestDataFactory.setSalePriceAndTaxType(em, iva19ProductId, IVA19_PRICE, "IVA_19");

            var clientId = TestDataFactory.createClientThirdParty(
                    em, "DSC-" + UUID.randomUUID().toString().substring(0, 8), "Discount Totals Client");

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
                    "ORD-DSC-" + UUID.randomUUID().toString().substring(0, 6),
                    clientId, warehouseId,
                    List.of(
                            new TestDataFactory.OrderLine(
                                    exentoProductId, exentoBatchId, BigDecimal.ONE,
                                    "PCT", new BigDecimal("10")),
                            new TestDataFactory.OrderLine(
                                    iva19ProductId, iva19BatchId, IVA19_QTY,
                                    "AMOUNT", new BigDecimal("500"))
                    ));

            var cashRegisterId = TestDataFactory.createCashRegister(
                    em, "DSC-REG-" + UUID.randomUUID().toString().substring(0, 8));
            shiftId = TestDataFactory.createOpenShift(em, TEST_USER_ID, cashRegisterId);
            em.flush();
        });
    }

    @Test
    void checkoutAppliesLineAndDocumentDiscountsBeforeIva() {
        // Line discounts ride on the order line; the document discount comes on checkout.
        var request = new CheckoutRequest(
                orderId, null, shiftId,
                List.of(new CheckoutRequest.PaymentLine("CASH", new BigDecimal("25000.00"))),
                "PCT", new BigDecimal("10"), "Descuento de prueba", null);

        checkoutUseCase.checkout(request, TEST_USER_ID);

        var invoice = loadInvoice();

        // Line discounts: EXENTO gross 10,000 − 10% (1,000) = 9,000 base gravable
        //                 IVA_19  gross 10,000 − 500     = 9,500 base gravable
        // netBeforeDoc = 18,500. Document discount 10% = 1,850 (prorated 900 / 950).
        // Final bases: 8,100 (EXENTO) + 8,550 (IVA_19).
        // tax0 = 0.00; tax19 = 8,550 × 19% = 1,624.50 (NOT 1,900.00 on the list price)
        assertThat(invoice.getTotalNet()).isEqualByComparingTo("16650.00");
        assertThat(invoice.getTotalTax0()).isEqualByComparingTo("0.00");
        assertThat(invoice.getTotalTax5()).isEqualByComparingTo("0.00");
        assertThat(invoice.getTotalTax19()).isEqualByComparingTo("1624.50");
        assertThat(invoice.getTotalAmount()).isEqualByComparingTo("18274.50");

        // discount_total = line discounts (1,000 + 500) + document discount (1,850)
        assertThat(invoice.getDiscountTotal()).isEqualByComparingTo("3350.00");
        assertThat(invoice.getDocumentDiscount()).isEqualByComparingTo("1850.00");
        assertThat(invoice.getDocumentDiscountType()).isEqualTo("PCT");
        assertThat(invoice.getDocumentDiscountValue()).isEqualByComparingTo("10.00");
        assertThat(invoice.getDiscountReason()).isEqualTo("Descuento de prueba");

        // The line-level discount is persisted per item, and each order line must be
        // persisted exactly once (N order lines -> N sales_items rows). The document
        // cascade owns the insert; there is no separate per-item save.
        var items = loadInvoiceItems(invoice.getId());
        assertThat(items).hasSize(2);
        assertThat(countItemsByDocumentId(invoice.getId())).isEqualTo(2L);

        var exentoItems = items.stream()
                .filter(i -> i.getProductId().equals(exentoProductId)).toList();
        var iva19Items = items.stream()
                .filter(i -> i.getProductId().equals(iva19ProductId)).toList();

        assertThat(exentoItems).isNotEmpty();
        exentoItems.forEach(i -> {
            assertThat(i.getDiscountType()).isEqualTo("PCT");
            assertThat(i.getDiscountValue()).isEqualByComparingTo("10.00");
            assertThat(i.getDiscountAmount()).isEqualByComparingTo("1000.00");
        });

        assertThat(iva19Items).isNotEmpty();
        iva19Items.forEach(i -> {
            assertThat(i.getDiscountType()).isEqualTo("AMOUNT");
            assertThat(i.getDiscountValue()).isEqualByComparingTo("500.00");
            assertThat(i.getDiscountAmount()).isEqualByComparingTo("500.00");
        });
    }

    private SalesDocumentEntity loadInvoice() {
        return new TransactionTemplate(transactionManager).execute(s -> em.createQuery(
                        "select d from SalesDocumentEntity d "
                                + "where d.type = :type and d.sourceDocumentId = :orderId",
                        SalesDocumentEntity.class)
                .setParameter("type", SalesDocumentType.INVOICE)
                .setParameter("orderId", orderId)
                .getSingleResult());
    }

    private List<SaleItemEntity> loadInvoiceItems(UUID documentId) {
        return new TransactionTemplate(transactionManager).execute(s -> em.createQuery(
                        "select i from SaleItemEntity i where i.document.id = :docId order by i.lineNumber",
                        SaleItemEntity.class)
                .setParameter("docId", documentId)
                .getResultList());
    }

    private long countItemsByDocumentId(UUID documentId) {
        return new TransactionTemplate(transactionManager).execute(s -> em.createQuery(
                        "select count(i) from SaleItemEntity i where i.document.id = :docId", Long.class)
                .setParameter("docId", documentId)
                .getSingleResult());
    }
}
