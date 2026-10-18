package co.posinvent.integration;

import co.posinvent.application.dto.PurchaseOrderRequest;
import co.posinvent.application.dto.PurchaseOrderResponse;
import co.posinvent.application.usecase.PurchaseOrderUseCase;
import co.posinvent.domain.exception.ResourceNotFoundException;
import co.posinvent.domain.model.ThirdParty;
import co.posinvent.domain.model.Warehouse;
import co.posinvent.infrastructure.adapters.out.persistence.TestDataFactory;
import co.posinvent.infrastructure.adapters.out.persistence.ThirdPartyEntity;
import co.posinvent.infrastructure.adapters.out.security.PosUserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration guards for the Compras OC hardening findings A5 and A6.
 *
 * <ul>
 *   <li><b>A5 — race-free numbering:</b> the old {@code findFirstByDocumentNumber...} + parse + 1
 *       scheme produced duplicate {@code document_number} values under concurrency. A database
 *       sequence ({@code V132}) plus a UNIQUE constraint remove the race: concurrent creations must
 *       yield N distinct numbers in the {@code OC-{yyyyMMdd}-{seq}} shape.</li>
 *   <li><b>A6 — reference validation on create:</b> a line referencing an unknown warehouse or
 *       product must fail as a business/not-found error, never as a raw FK violation (500).</li>
 * </ul>
 *
 * <p>Deliberately NOT {@code @Transactional}: each worker thread owns its own transaction so the
 * inserts commit (and the unique constraint is enforced) before the run ends.</p>
 */
class PurchaseOrderHardeningIntegrationTest extends AbstractIntegrationTest {

    private static final int ORDERS = 25;
    private static final BigDecimal QTY = new BigDecimal("1.000");
    private static final BigDecimal UNIT_COST = new BigDecimal("100.00");

    @Autowired private PurchaseOrderUseCase purchaseOrderUseCase;

    private UUID supplierId;
    private UUID warehouseId;
    private UUID productId;

    @BeforeEach
    void setUp() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            warehouseId = TestDataFactory.createWarehouse(em, "PO Hardening WH", Warehouse.WarehouseType.CORTES);
            productId = TestDataFactory.createProduct(em, "POHARD", "Producto OC hardening", false, true);

            var supplier = new ThirdPartyEntity();
            supplier.setNumIdentification("SUP-POHARD-" + UUID.randomUUID().toString().substring(0, 8));
            supplier.setName("Supplier OC Hardening");
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
    void concurrentCreationProducesDistinctDocumentNumbers() throws Exception {
        String datePart = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);

        ExecutorService pool = Executors.newFixedThreadPool(ORDERS);
        CountDownLatch startGate = new CountDownLatch(1);
        List<Throwable> failures = Collections.synchronizedList(new ArrayList<>());
        List<PurchaseOrderResponse> created = Collections.synchronizedList(new ArrayList<>());
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < ORDERS; i++) {
            futures.add(pool.submit(() -> {
                try {
                    authenticateWorker();
                    startGate.await();
                    created.add(purchaseOrderUseCase.create(request(warehouseId, productId), TEST_USER_ID));
                } catch (Throwable t) {
                    failures.add(t);
                } finally {
                    SecurityContextHolder.clearContext();
                }
                return null;
            }));
        }

        startGate.countDown();
        for (Future<?> future : futures) {
            future.get(3, TimeUnit.MINUTES);
        }
        pool.shutdown();

        // (a) No duplicate-key violation reaches any caller.
        assertThat(failures).isEmpty();
        assertThat(created).hasSize(ORDERS);

        // (b) Every order got a distinct number in the OC-{yyyyMMdd}-{seq} shape.
        List<String> numbers = created.stream().map(PurchaseOrderResponse::documentNumber).toList();
        assertThat(numbers).doesNotHaveDuplicates();
        assertThat(numbers).allSatisfy(n -> assertThat(n).startsWith("OC-" + datePart + "-"));
    }

    @Test
    void createWithUnknownWarehouseFailsAsNotFoundNotFkViolation() {
        var unknownWarehouse = UUID.randomUUID();

        assertThatThrownBy(() -> purchaseOrderUseCase.create(
                request(unknownWarehouse, productId), TEST_USER_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void createWithUnknownProductFailsAsNotFoundNotFkViolation() {
        var unknownProduct = UUID.randomUUID();

        assertThatThrownBy(() -> purchaseOrderUseCase.create(
                request(warehouseId, unknownProduct), TEST_USER_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private PurchaseOrderRequest request(UUID warehouseId, UUID productId) {
        return new PurchaseOrderRequest(
                supplierId,
                LocalDate.now(),
                null,
                null,
                null,
                null,
                null,
                null,
                "COP",
                List.of(new PurchaseOrderRequest.LineItem(
                        productId, QTY, UNIT_COST, warehouseId, null, "EXENTO")));
    }

    private static void authenticateWorker() {
        var principal = new PosUserDetails(
                TEST_USER_ID, "test-user", "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")), null);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }
}
