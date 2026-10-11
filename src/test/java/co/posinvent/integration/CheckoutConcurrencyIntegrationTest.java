package co.posinvent.integration;

import co.posinvent.application.usecase.CheckoutRequest;
import co.posinvent.application.usecase.PosCheckoutUseCase;
import co.posinvent.domain.model.Batch;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for POS checkout concurrency hardening (audit C4).
 *
 * <p>N distinct CONFIRMED orders share the same client, warehouse, product and
 * batch, so every checkout contends on the single {@code inventory_stock} row.
 * Before the fix the checkout relied only on {@code @Version}, so concurrent
 * checkouts surfaced {@link org.springframework.dao.OptimisticLockingFailureException}
 * to the cashier. After the fix the checkout is wrapped in
 * {@code OptimisticConcurrencyExecutor}, which retries the whole unit of work in a
 * fresh transaction, so no failure reaches the caller and no update is lost.</p>
 *
 * <p>The class is deliberately NOT {@code @Transactional}: setup commits via a
 * {@link TransactionTemplate} so the worker threads (each opening its own
 * REQUIRES_NEW transaction inside the executor) observe the committed rows,
 * exactly like production callers.</p>
 */
class CheckoutConcurrencyIntegrationTest extends AbstractIntegrationTest {

    private static final int ORDERS = 30;
    private static final BigDecimal SALE_PRICE = new BigDecimal("1000.00");
    private static final BigDecimal STOCK_QUANTITY = new BigDecimal("1000");

    @Autowired private PosCheckoutUseCase checkoutUseCase;
    @Autowired private SalesDocumentRepository documentRepo;
    @Autowired private StockRepository stockRepo;
    @Autowired private ThirdPartyRepository thirdPartyRepo;

    private UUID warehouseId;
    private UUID productId;
    private UUID batchId;
    private UUID clientId;
    private final List<UUID> orderIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        orderIds.clear();

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            warehouseId = TestDataFactory.createWarehouse(
                    em, "CHECKOUT CONC", Warehouse.WarehouseType.CORTES);
            productId = TestDataFactory.createProduct(
                    em, "TEST-CHK", "Checkout Concurrency", false, true);
            em.flush();

            // Simple, tax-free pricing: total = salePrice * qty (EXENTO => 0% tax).
            TestDataFactory.setSalePriceAndTaxType(em, productId, SALE_PRICE, "EXENTO");

            clientId = TestDataFactory.createClientThirdParty(
                    em, "CHK-" + UUID.randomUUID().toString().substring(0, 8), "Checkout Concurrency Client");

            var systemSupplier = thirdPartyRepo.findByNumIdentification("000000000-0").orElseThrow();
            batchId = TestDataFactory.createBatch(
                    em, productId, systemSupplier.id(), warehouseId,
                    STOCK_QUANTITY, Batch.BatchStatus.OPEN);
            TestDataFactory.createStock(
                    em, productId, batchId, warehouseId, STOCK_QUANTITY, BigDecimal.ZERO);

            for (int i = 0; i < ORDERS; i++) {
                orderIds.add(TestDataFactory.createConfirmedOrder(
                        em,
                        "ORD-CHK-" + i + "-" + UUID.randomUUID().toString().substring(0, 6),
                        clientId, warehouseId, productId, batchId, BigDecimal.ONE));
            }
            em.flush();
        });
    }

    @Test
    void concurrentCheckoutsForSameProductNeverFailAndLoseNoStock() throws Exception {
        int workers = orderIds.size();
        ExecutorService pool = Executors.newFixedThreadPool(workers);
        CountDownLatch startGate = new CountDownLatch(1);
        List<Throwable> failures = Collections.synchronizedList(new ArrayList<>());
        List<Future<?>> futures = new ArrayList<>();

        for (UUID orderId : orderIds) {
            futures.add(pool.submit(() -> {
                try {
                    startGate.await();
                    // cashRegisterId is nullable and unused by the use case; keep it null
                    // to avoid an unnecessary FK to cash_registers in this test.
                    var request = new CheckoutRequest(
                            orderId, null,
                            List.of(new CheckoutRequest.PaymentLine("CASH", SALE_PRICE)));
                    checkoutUseCase.checkout(request, TEST_USER_ID);
                } catch (Throwable t) {
                    failures.add(t);
                }
                return null;
            }));
        }

        startGate.countDown();

        for (Future<?> future : futures) {
            future.get(3, TimeUnit.MINUTES);
        }
        pool.shutdown();

        // (a) No optimistic-lock failure ever reaches the cashier.
        assertThat(failures).isEmpty();

        // (b) Final stock = initial - N: no lost update, no oversell.
        var stock = stockRepo.findByProductBatchWarehouse(productId, batchId, warehouseId).orElseThrow();
        assertThat(stock.currentQuantity())
                .isEqualByComparingTo(STOCK_QUANTITY.subtract(new BigDecimal(workers)));

        // (c) Every order was invoiced. Read inside a transaction: the document
        // mapper eagerly touches the lazy items collection during mapping.
        var tx = new TransactionTemplate(transactionManager);
        for (UUID orderId : orderIds) {
            var status = tx.execute(s -> documentRepo.findById(orderId).orElseThrow().status());
            assertThat(status).isEqualTo(SalesDocumentStatus.INVOICED);
        }
    }
}
