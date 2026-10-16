package co.posinvent.integration;

import co.posinvent.application.usecase.CheckoutRequest;
import co.posinvent.application.usecase.PosCheckoutUseCase;
import co.posinvent.domain.model.Batch;
import co.posinvent.domain.model.SalesDocumentType;
import co.posinvent.domain.model.Warehouse;
import co.posinvent.domain.repository.PucAccountRepository;
import co.posinvent.domain.repository.ThirdPartyRepository;
import co.posinvent.infrastructure.adapters.out.persistence.JournalEntryEntity;
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
 * Regression guard for the COGS source of truth (Contabilidad Fase 2, hallazgo C2).
 *
 * <p>The SALE journal must value {@code SALE_COGS} (debit 6135) and
 * {@code SALE_INVENTORY_OUT} (credit 1435) at the REAL cost taken from the Kardex —
 * {@code quantity × unitCost} of the sale's EXIT movements — and never at the sale
 * price ({@code item.subtotal()}).</p>
 *
 * <p>This test seeds stock with a known unit cost, checks out a confirmed order whose
 * sale price differs from the cost, and asserts both COGS lines equal the Kardex cost.</p>
 */
class SaleCogsFromKardexIntegrationTest extends AbstractIntegrationTest {

    private static final BigDecimal UNIT_COST = new BigDecimal("3000.00");
    private static final BigDecimal SALE_PRICE = new BigDecimal("10000.00");
    private static final BigDecimal QTY = new BigDecimal("2");
    private static final BigDecimal EXPECTED_COGS = new BigDecimal("6000.00");   // 2 × 3,000.00
    private static final BigDecimal SALE_SUBTOTAL = new BigDecimal("20000.00");  // 2 × 10,000.00 (sale price)

    @Autowired private PosCheckoutUseCase checkoutUseCase;
    @Autowired private ThirdPartyRepository thirdPartyRepo;
    @Autowired private PucAccountRepository pucRepo;

    private UUID orderId;

    @BeforeEach
    void setUp() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            var warehouseId = TestDataFactory.createWarehouse(
                    em, "COGS WU31", Warehouse.WarehouseType.CORTES);

            var productId = TestDataFactory.createProduct(
                    em, "TEST-COGS", "Cogs Product", false, true);
            em.flush();
            TestDataFactory.setSalePriceAndTaxType(em, productId, SALE_PRICE, "EXENTO");

            var clientId = TestDataFactory.createClientThirdParty(
                    em, "COGS-" + UUID.randomUUID().toString().substring(0, 8), "Cogs Client");

            var systemSupplier = thirdPartyRepo.findByNumIdentification("000000000-0").orElseThrow();
            var batchId = TestDataFactory.createBatch(
                    em, productId, systemSupplier.id(), warehouseId,
                    new BigDecimal("100"), Batch.BatchStatus.OPEN);
            TestDataFactory.createStock(
                    em, productId, batchId, warehouseId, new BigDecimal("100"), UNIT_COST);

            TestDataFactory.createActiveDianResolution(
                    em, "COGS", 1L, 100_000L,
                    LocalDate.now().minusDays(1), LocalDate.now().plusDays(1));

            orderId = TestDataFactory.createConfirmedOrder(
                    em,
                    "ORD-COGS-" + UUID.randomUUID().toString().substring(0, 6),
                    clientId, warehouseId, productId, batchId, QTY);
            em.flush();
        });
    }

    @Test
    void saleJournalValuesCogsAtKardexCostNotSalePrice() {
        var request = new CheckoutRequest(orderId, null,
                List.of(new CheckoutRequest.PaymentLine("CASH", SALE_SUBTOTAL)));

        checkoutUseCase.checkout(request, TEST_USER_ID);

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            var invoice = em.createQuery(
                            "select d from SalesDocumentEntity d "
                                    + "where d.type = :type and d.sourceDocumentId = :orderId",
                            SalesDocumentEntity.class)
                    .setParameter("type", SalesDocumentType.INVOICE)
                    .setParameter("orderId", orderId)
                    .getSingleResult();

            assertThat(invoice.getTotalNet()).isEqualByComparingTo(SALE_SUBTOTAL);

            var cogsAccountId = pucRepo.findByCode("6135").orElseThrow().id();
            var inventoryAccountId = pucRepo.findByCode("1435").orElseThrow().id();

            var entry = em.createQuery(
                            "select e from JournalEntryEntity e "
                                    + "where e.sourceType = 'SALE' and e.sourceId = :id",
                            JournalEntryEntity.class)
                    .setParameter("id", invoice.getId())
                    .getSingleResult();

            var cogsLine = entry.getLines().stream()
                    .filter(l -> cogsAccountId.equals(l.getAccountId()))
                    .findFirst().orElseThrow();
            var inventoryLine = entry.getLines().stream()
                    .filter(l -> inventoryAccountId.equals(l.getAccountId()))
                    .findFirst().orElseThrow();

            // COGS and inventory-out are valued at the Kardex cost (qty × unitCost)...
            assertThat(cogsLine.getDebit()).isEqualByComparingTo(EXPECTED_COGS);
            assertThat(inventoryLine.getCredit()).isEqualByComparingTo(EXPECTED_COGS);

            // ...and NOT at the sale price (the C2 defect this WU fixes).
            assertThat(cogsLine.getDebit()).isNotEqualByComparingTo(SALE_SUBTOTAL);
        });
    }
}
