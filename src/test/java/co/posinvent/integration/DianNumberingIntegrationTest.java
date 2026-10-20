package co.posinvent.integration;

import co.posinvent.application.usecase.CheckoutRequest;
import co.posinvent.application.usecase.PosCheckoutUseCase;
import co.posinvent.domain.model.Batch;
import co.posinvent.domain.model.SalesDocumentType;
import co.posinvent.domain.model.Warehouse;
import co.posinvent.domain.repository.DianResolutionRepository;
import co.posinvent.domain.repository.StockRepository;
import co.posinvent.domain.repository.ThirdPartyRepository;
import co.posinvent.infrastructure.adapters.out.persistence.TestDataFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.concurrent.DelegatingSecurityContextRunnable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
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
 * Integration test for resolution-based fiscal invoice numbering (audit C2).
 *
 * <p>N distinct CONFIRMED orders share the same client, warehouse, product and
 * batch. All N checkouts run concurrently; each must obtain a distinct invoice
 * number from the ACTIVE DIAN resolution, and the persisted {@code current_number}
 * must advance exactly N times. The pessimistic lock on the active resolution row
 * is what makes the consecutive number safe under contention.</p>
 *
 * <p>The class is deliberately NOT {@code @Transactional}: setup commits via a
 * {@link TransactionTemplate} so each worker thread (owning its own REQUIRES_NEW
 * transaction) observes the committed resolution and stock rows.</p>
 */
class DianNumberingIntegrationTest extends AbstractIntegrationTest {

    private static final int ORDERS = 20;
    private static final String PREFIX = "NUM";
    private static final long RANGE_FROM = 1L;
    private static final long RANGE_TO = 100_000L;
    private static final BigDecimal SALE_PRICE = new BigDecimal("1000.00");
    private static final BigDecimal STOCK_QUANTITY = new BigDecimal("1000");

    @Autowired private PosCheckoutUseCase checkoutUseCase;
    @Autowired private StockRepository stockRepo;
    @Autowired private ThirdPartyRepository thirdPartyRepo;
    @Autowired private DianResolutionRepository dianResolutionRepo;

    private UUID warehouseId;
    private UUID productId;
    private UUID batchId;
    private UUID clientId;
    private UUID shiftId;
    private final List<UUID> orderIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        orderIds.clear();

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            warehouseId = TestDataFactory.createWarehouse(
                    em, "DIAN NUM", Warehouse.WarehouseType.CORTES);
            productId = TestDataFactory.createProduct(
                    em, "TEST-DIAN", "Dian Numbering", false, true);
            em.flush();

            TestDataFactory.setSalePriceAndTaxType(em, productId, SALE_PRICE, "EXENTO");

            clientId = TestDataFactory.createClientThirdParty(
                    em, "DN-" + UUID.randomUUID().toString().substring(0, 8), "Dian Numbering Client");

            var systemSupplier = thirdPartyRepo.findByNumIdentification("000000000-0").orElseThrow();
            batchId = TestDataFactory.createBatch(
                    em, productId, systemSupplier.id(), warehouseId,
                    STOCK_QUANTITY, Batch.BatchStatus.OPEN);
            TestDataFactory.createStock(
                    em, productId, batchId, warehouseId, STOCK_QUANTITY, BigDecimal.ZERO);

            TestDataFactory.createActiveDianResolution(
                    em, PREFIX, RANGE_FROM, RANGE_TO,
                    LocalDate.now().minusDays(1), LocalDate.now().plusDays(1));

            var cashRegisterId = TestDataFactory.createCashRegister(
                    em, "DIAN-REG-" + UUID.randomUUID().toString().substring(0, 8));
            shiftId = TestDataFactory.createOpenShift(em, TEST_USER_ID, cashRegisterId);

            for (int i = 0; i < ORDERS; i++) {
                orderIds.add(TestDataFactory.createConfirmedOrder(
                        em,
                        "ORD-DN-" + i + "-" + UUID.randomUUID().toString().substring(0, 6),
                        clientId, warehouseId, productId, batchId, BigDecimal.ONE));
            }
            em.flush();
        });
    }

    @Test
    void concurrentCheckoutsIssueDistinctSequentialInvoiceNumbers() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(ORDERS);
        CountDownLatch startGate = new CountDownLatch(1);
        List<Throwable> failures = Collections.synchronizedList(new ArrayList<>());
        List<Future<?>> futures = new ArrayList<>();

        for (UUID orderId : orderIds) {
            futures.add(pool.submit(DelegatingSecurityContextRunnable.create(() -> {
                try {
                    startGate.await();
                    var request = new CheckoutRequest(
                            orderId, null, shiftId,
                            List.of(new CheckoutRequest.PaymentLine("CASH", SALE_PRICE)));
                    checkoutUseCase.checkout(request);
                } catch (Throwable t) {
                    failures.add(t);
                }
            }, SecurityContextHolder.getContext())));
        }

        startGate.countDown();

        for (Future<?> future : futures) {
            future.get(3, TimeUnit.MINUTES);
        }
        pool.shutdown();

        // (a) No failure ever reaches the cashier.
        assertThat(failures).isEmpty();

        var tx = new TransactionTemplate(transactionManager);

        // (b) Exactly N distinct invoice numbers, all from the active resolution.
        List<String> numbers = tx.execute(s -> em.createQuery(
                        "select d.documentNumber from SalesDocumentEntity d "
                                + "where d.type = :type and d.sourceDocumentId in :ids",
                        String.class)
                .setParameter("type", SalesDocumentType.INVOICE)
                .setParameter("ids", orderIds)
                .getResultList());

        assertThat(numbers).hasSize(ORDERS);
        assertThat(numbers).doesNotHaveDuplicates();
        assertThat(numbers).allSatisfy(n -> assertThat(n).startsWith(PREFIX));

        var numeric = numbers.stream()
                .map(n -> Long.parseLong(n.substring(PREFIX.length())))
                .toList();
        assertThat(numeric).allSatisfy(v -> assertThat(v).isBetween(RANGE_FROM, RANGE_TO));

        // (c) The consecutive advanced exactly once per checkout.
        var current = tx.execute(s -> dianResolutionRepo.findActive().orElseThrow().currentNumber());
        assertThat(current).isEqualTo(RANGE_FROM + ORDERS - 1);
    }
}
