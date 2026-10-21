package co.posinvent.integration;

import co.posinvent.application.usecase.CheckoutRequest;
import co.posinvent.application.usecase.PosCheckoutUseCase;
import co.posinvent.domain.exception.BusinessException;
import co.posinvent.domain.model.Batch;
import co.posinvent.domain.model.InventoryStock;
import co.posinvent.domain.model.SalesDocumentStatus;
import co.posinvent.domain.model.Warehouse;
import co.posinvent.domain.repository.SalesDocumentRepository;
import co.posinvent.domain.repository.StockRepository;
import co.posinvent.domain.repository.ThirdPartyRepository;
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
 * Integration test for stock reservation consumption (audit A1).
 *
 * <p>Seeds a CONFIRMED ORDER whose stock row already carries a booked reservation
 * ({@code committedQuantity > 0}), then runs the POS checkout and asserts that the
 * finished sale consumed the reservation: both {@code currentQuantity} and
 * {@code committedQuantity} drop by the sold quantity with no drift.</p>
 *
 * <p>The class is deliberately NOT {@code @Transactional}: the fixture commits via a
 * {@link TransactionTemplate} so the checkout (which opens its own transaction)
 * observes the committed rows, exactly like production callers.</p>
 */
class StockReservationIntegrationTest extends AbstractIntegrationTest {

    private static final BigDecimal SALE_PRICE = new BigDecimal("1000.00");
    private static final BigDecimal STOCK_QUANTITY = new BigDecimal("100");
    private static final BigDecimal ORDER_QUANTITY = new BigDecimal("10");
    private static final BigDecimal COMMITTED_QUANTITY = new BigDecimal("10");

    @Autowired private PosCheckoutUseCase checkoutUseCase;
    @Autowired private StockRepository stockRepo;
    @Autowired private SalesDocumentRepository documentRepo;
    @Autowired private ThirdPartyRepository thirdPartyRepo;

    private UUID warehouseId;
    private UUID productId;
    private UUID batchId;
    private UUID clientId;
    private UUID orderId;
    private UUID shiftId;

    @BeforeEach
    void setUp() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            warehouseId = TestDataFactory.createWarehouse(
                    em, "RESERVATION Test", Warehouse.WarehouseType.CORTES);
            productId = TestDataFactory.createProduct(
                    em, "TEST-RSV", "Stock Reservation", false, true);
            em.flush();

            // Simple, tax-free pricing: total = salePrice * qty (EXENTO => 0% tax).
            TestDataFactory.setSalePriceAndTaxType(em, productId, SALE_PRICE, "EXENTO");

            clientId = TestDataFactory.createClientThirdParty(
                    em, "RSV-" + UUID.randomUUID().toString().substring(0, 8), "Reservation Client");

            var systemSupplier = thirdPartyRepo.findByNumIdentification("000000000-0").orElseThrow();
            batchId = TestDataFactory.createBatch(
                    em, productId, systemSupplier.id(), warehouseId,
                    STOCK_QUANTITY, Batch.BatchStatus.OPEN);
            TestDataFactory.createStock(
                    em, productId, batchId, warehouseId, STOCK_QUANTITY, BigDecimal.ZERO);
            em.flush();

            // Simulate the reservation booked when the ORDER was confirmed.
            var seeded = stockRepo.findByProductBatchWarehouse(productId, batchId, warehouseId).orElseThrow();
            stockRepo.save(new InventoryStock(
                    seeded.id(), seeded.productId(), seeded.batchId(), seeded.warehouseId(),
                    seeded.currentQuantity(), COMMITTED_QUANTITY,
                    seeded.unitCost(), seeded.createdAt(), seeded.updatedAt()));

            // Checkout requires an ACTIVE, valid DIAN resolution to number invoices.
            TestDataFactory.createActiveDianResolution(
                    em, "RSV", 1L, 100000L, LocalDate.now().minusDays(1), LocalDate.now().plusDays(1));

            orderId = TestDataFactory.createConfirmedOrder(
                    em, "ORD-RSV-" + UUID.randomUUID().toString().substring(0, 6),
                    clientId, warehouseId, productId, batchId, ORDER_QUANTITY);

            var cashRegisterId = TestDataFactory.createCashRegister(
                    em, "RSV-REG-" + UUID.randomUUID().toString().substring(0, 8));
            shiftId = TestDataFactory.createOpenShift(em, TEST_USER_ID, cashRegisterId);
            em.flush();
        });
    }

    @Test
    void checkoutConsumesReservationOnceAndPersistsTheInvoiceIssued() {
        var request = new CheckoutRequest(
                orderId, null, shiftId,
                List.of(new CheckoutRequest.PaymentLine("CASH", SALE_PRICE.multiply(ORDER_QUANTITY))));

        var response = checkoutUseCase.checkout(request);

        var invoice = documentRepo.findByIdWithItems(response.invoiceId()).orElseThrow();
        assertThat(invoice.status()).isEqualTo(SalesDocumentStatus.ISSUED);

        var stock = stockRepo.findByProductBatchWarehouse(productId, batchId, warehouseId).orElseThrow();
        assertThat(stock.currentQuantity())
                .isEqualByComparingTo(STOCK_QUANTITY.subtract(ORDER_QUANTITY));   // 90
        assertThat(stock.committedQuantity())
                .isEqualByComparingTo(COMMITTED_QUANTITY.subtract(ORDER_QUANTITY)); // 0

        // A retried checkout is rejected before touching stock again.
        assertThatThrownBy(() -> checkoutUseCase.checkout(request))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo("POS_ORDER_NOT_CONFIRMED"));

        var unchanged = stockRepo.findByProductBatchWarehouse(productId, batchId, warehouseId).orElseThrow();
        assertThat(unchanged.currentQuantity())
                .isEqualByComparingTo(STOCK_QUANTITY.subtract(ORDER_QUANTITY));
        assertThat(unchanged.committedQuantity())
                .isEqualByComparingTo(COMMITTED_QUANTITY.subtract(ORDER_QUANTITY));
    }
}
