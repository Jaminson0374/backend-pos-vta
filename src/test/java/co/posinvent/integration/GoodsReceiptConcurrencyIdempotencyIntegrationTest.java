package co.posinvent.integration;

import co.posinvent.application.dto.GoodsReceiptRequest;
import co.posinvent.application.usecase.CreateGoodsReceiptUseCase;
import co.posinvent.domain.model.PurchaseLineItem;
import co.posinvent.domain.model.PurchaseOrder;
import co.posinvent.domain.model.PurchaseOrderStatus;
import co.posinvent.domain.model.ThirdParty;
import co.posinvent.domain.model.Warehouse;
import co.posinvent.domain.repository.PurchaseOrderRepository;
import co.posinvent.infrastructure.adapters.out.persistence.TestDataFactory;
import co.posinvent.infrastructure.adapters.out.persistence.ThirdPartyEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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
 * Compras WU-41 — integration guards for the goods-receipt audit findings C3/A8/A4.
 *
 * <ul>
 *   <li><b>C3/A8 — serialized, idempotent receiving:</b> concurrent receipts of the same OC must
 *       never over-receive (the OC is locked for the duration of the transaction) and a retried
 *       submission carrying the same {@code idempotencyKey} must register at most one receipt,
 *       affecting stock exactly once.</li>
 *   <li><b>A4 — OC state machine:</b> a single complete receipt moves the OC PENDING → RECEIVED
 *       through the validated domain transition.</li>
 * </ul>
 *
 * <p>Deliberately NOT {@code @Transactional}: fixtures are committed through a
 * {@link TransactionTemplate} so the concurrent workers observe committed rows in their own
 * transactions, exactly like production callers.</p>
 */
class GoodsReceiptConcurrencyIdempotencyIntegrationTest extends AbstractIntegrationTest {

    private static final BigDecimal ACTUAL_UNIT_COST = new BigDecimal("100.00");

    @Autowired private CreateGoodsReceiptUseCase createGoodsReceiptUseCase;
    @Autowired private PurchaseOrderRepository purchaseOrderRepository;

    private UUID supplierId;
    private UUID warehouseId;
    private UUID productId;

    @BeforeEach
    void setUp() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            warehouseId = TestDataFactory.createWarehouse(em, "GR WU41", Warehouse.WarehouseType.CORTES);
            productId = TestDataFactory.createProduct(em, "GRWU41", "Producto recepción WU41", false, true);

            var supplier = new ThirdPartyEntity();
            supplier.setNumIdentification("SUP-WU41-" + UUID.randomUUID().toString().substring(0, 8));
            supplier.setName("Supplier Recepción WU41");
            supplier.setType(ThirdParty.ThirdPartyType.SUPPLIER);
            supplier.setPersonType(ThirdParty.PersonType.JURIDICA);
            supplier.setTaxRegime(ThirdParty.TaxRegime.ORDINARIO);
            supplier.setTaxResponsibilities(List.of());
            supplier.setActive(true);
            supplier.setCreditLimit(BigDecimal.ZERO);
            em.persist(supplier);
            supplierId = supplier.getId();
        });
    }

    @Test
    void concurrentReceiptsNeverExceedOrderedQuantity() throws Exception {
        int ordered = 4;
        var ocId = createOc(new BigDecimal(ordered));
        int attempts = 8; // twice the ordered quantity, so the guard must reject the surplus

        ExecutorService pool = Executors.newFixedThreadPool(attempts);
        CountDownLatch startGate = new CountDownLatch(1);
        List<Throwable> failures = Collections.synchronizedList(new ArrayList<>());
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < attempts; i++) {
            futures.add(pool.submit(() -> {
                try {
                    startGate.await();
                    createGoodsReceiptUseCase.process(new GoodsReceiptRequest(
                            ocId,
                            List.of(new GoodsReceiptRequest.ReceiptLine(
                                    productId, warehouseId, BigDecimal.ONE, ACTUAL_UNIT_COST, null))),
                            TEST_USER_ID);
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

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            var oc = purchaseOrderRepository.findById(ocId).orElseThrow();
            var received = oc.lines().get(0).receivedQty();

            // The received quantity must never exceed what was ordered.
            assertThat(received).isLessThanOrEqualTo(new BigDecimal(ordered));
            assertThat(received).isEqualByComparingTo(new BigDecimal(ordered));
            assertThat(oc.status()).isEqualTo(PurchaseOrderStatus.RECEIVED);

            // Exactly one receipt per accepted unit — no double stock, no double receivedQty.
            assertThat(countReceipts(ocId)).isEqualTo(ordered);
            assertThat(sumStock(productId)).isEqualByComparingTo(new BigDecimal(ordered));
        });
    }

    @Test
    void fullReceiptOfPendingOrderTransitionsToReceived() {
        var ocId = createOc(BigDecimal.ONE);

        createGoodsReceiptUseCase.process(new GoodsReceiptRequest(
                ocId,
                List.of(new GoodsReceiptRequest.ReceiptLine(
                        productId, warehouseId, BigDecimal.ONE, ACTUAL_UNIT_COST, null))),
                TEST_USER_ID);

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            var oc = purchaseOrderRepository.findById(ocId).orElseThrow();
            assertThat(oc.status()).isEqualTo(PurchaseOrderStatus.RECEIVED);
            assertThat(oc.lines().get(0).receivedQty()).isEqualByComparingTo(BigDecimal.ONE);
        });
    }

    @Test
    void repeatedIdempotencyKeyRegistersTheReceiptOnlyOnce() {
        var ocId = createOc(new BigDecimal("4"));
        var idempotencyKey = "wu41-key-" + UUID.randomUUID();

        var first = createGoodsReceiptUseCase.process(new GoodsReceiptRequest(
                ocId,
                List.of(new GoodsReceiptRequest.ReceiptLine(
                        productId, warehouseId, BigDecimal.ONE, ACTUAL_UNIT_COST, null)),
                idempotencyKey), TEST_USER_ID);

        var second = createGoodsReceiptUseCase.process(new GoodsReceiptRequest(
                ocId,
                List.of(new GoodsReceiptRequest.ReceiptLine(
                        productId, warehouseId, BigDecimal.ONE, ACTUAL_UNIT_COST, null)),
                idempotencyKey), TEST_USER_ID);

        assertThat(second.id()).isEqualTo(first.id());

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            var oc = purchaseOrderRepository.findById(ocId).orElseThrow();

            assertThat(countReceipts(ocId)).isEqualTo(1);
            assertThat(oc.lines().get(0).receivedQty()).isEqualByComparingTo(BigDecimal.ONE);
            assertThat(sumStock(productId)).isEqualByComparingTo(BigDecimal.ONE);
        });
    }

    @Test
    void distinctIdempotencyKeysCreateSeparateReceipts() {
        var ocId = createOc(new BigDecimal("4"));

        var first = createGoodsReceiptUseCase.process(new GoodsReceiptRequest(
                ocId,
                List.of(new GoodsReceiptRequest.ReceiptLine(
                        productId, warehouseId, BigDecimal.ONE, ACTUAL_UNIT_COST, null)),
                "wu41-key-a-" + UUID.randomUUID()), TEST_USER_ID);

        var second = createGoodsReceiptUseCase.process(new GoodsReceiptRequest(
                ocId,
                List.of(new GoodsReceiptRequest.ReceiptLine(
                        productId, warehouseId, BigDecimal.ONE, ACTUAL_UNIT_COST, null)),
                "wu41-key-b-" + UUID.randomUUID()), TEST_USER_ID);

        assertThat(second.id()).isNotEqualTo(first.id());

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            var oc = purchaseOrderRepository.findById(ocId).orElseThrow();

            assertThat(countReceipts(ocId)).isEqualTo(2);
            assertThat(oc.lines().get(0).receivedQty()).isEqualByComparingTo(new BigDecimal("2"));
            assertThat(sumStock(productId)).isEqualByComparingTo(new BigDecimal("2"));
        });
    }

    @Test
    void concurrentRetriesWithTheSameKeyRegisterExactlyOneReceipt() throws Exception {
        var ocId = createOc(new BigDecimal("4"));
        var idempotencyKey = "wu41-concurrent-key-" + UUID.randomUUID();
        int workers = 6;

        ExecutorService pool = Executors.newFixedThreadPool(workers);
        CountDownLatch startGate = new CountDownLatch(1);
        List<Throwable> failures = Collections.synchronizedList(new ArrayList<>());
        List<UUID> receiptIds = Collections.synchronizedList(new ArrayList<>());
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < workers; i++) {
            futures.add(pool.submit(() -> {
                try {
                    startGate.await();
                    var receipt = createGoodsReceiptUseCase.process(new GoodsReceiptRequest(
                            ocId,
                            List.of(new GoodsReceiptRequest.ReceiptLine(
                                    productId, warehouseId, BigDecimal.ONE, ACTUAL_UNIT_COST, null)),
                            idempotencyKey), TEST_USER_ID);
                    receiptIds.add(receipt.id());
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

        assertThat(failures).isEmpty();
        assertThat(receiptIds).hasSize(workers).containsOnly(receiptIds.get(0));

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            var oc = purchaseOrderRepository.findById(ocId).orElseThrow();

            assertThat(countReceipts(ocId)).isEqualTo(1);
            assertThat(oc.lines().get(0).receivedQty()).isEqualByComparingTo(BigDecimal.ONE);
            assertThat(sumStock(productId)).isEqualByComparingTo(BigDecimal.ONE);
        });
    }

    // ── helpers ─────────────────────────────────────────────────────────────
    private UUID createOc(BigDecimal orderedQty) {
        var holder = new UUID[1];
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            var line = new PurchaseLineItem(
                    null, null, productId, warehouseId, orderedQty,
                    BigDecimal.ZERO, ACTUAL_UNIT_COST, BigDecimal.ZERO, "EXENTO", 1);
            var oc = purchaseOrderRepository.save(new PurchaseOrder(
                    null, supplierId, PurchaseOrderStatus.PENDING, LocalDate.now(),
                    "OC-WU41-" + UUID.randomUUID().toString().substring(0, 6),
                    null, null, null, null, null, null, "COP",
                    TEST_USER_ID, null, null, null, List.of(line)));
            holder[0] = oc.id();
        });
        return holder[0];
    }

    private long countReceipts(UUID ocId) {
        return em.createQuery(
                        "select count(g) from GoodsReceiptEntity g where g.ocId = :ocId", Long.class)
                .setParameter("ocId", ocId)
                .getSingleResult();
    }

    private BigDecimal sumStock(UUID productId) {
        return em.createQuery(
                        "select coalesce(sum(s.currentQuantity), 0) from InventoryStockEntity s "
                                + "where s.productId = :productId", BigDecimal.class)
                .setParameter("productId", productId)
                .getSingleResult();
    }
}
