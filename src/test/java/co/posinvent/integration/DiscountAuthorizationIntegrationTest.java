package co.posinvent.integration;

import co.posinvent.application.usecase.AuthorizeDiscountUseCase;
import co.posinvent.application.usecase.CheckoutRequest;
import co.posinvent.application.usecase.PosCheckoutUseCase;
import co.posinvent.domain.exception.BusinessException;
import co.posinvent.domain.model.Batch;
import co.posinvent.domain.model.SalesDocumentType;
import co.posinvent.domain.model.Warehouse;
import co.posinvent.domain.repository.ThirdPartyRepository;
import co.posinvent.infrastructure.adapters.out.persistence.SalesDocumentEntity;
import co.posinvent.infrastructure.adapters.out.persistence.TestDataFactory;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * End-to-end proof of the single-use discount authorization flow (M2 / WU-27).
 *
 * <p>A document discount above the global ceiling ({@value #DISCOUNT_PCT}% &gt; 30%)
 * forces an administrator authorization. The checkout carries only the token id:
 * a valid token stamps {@code discount_authorized_by} on the invoice, a missing token
 * raises {@code DISCOUNT_AUTHORIZATION_REQUIRED} and a replay of the same token fails.</p>
 */
class DiscountAuthorizationIntegrationTest extends AbstractIntegrationTest {

    private static final BigDecimal PRICE = new BigDecimal("10000.00");
    private static final BigDecimal DISCOUNT_PCT = new BigDecimal("40");
    private static final String ADMIN_PASSWORD = "AdminSecret123!";

    @Autowired private PosCheckoutUseCase checkoutUseCase;
    @Autowired private AuthorizeDiscountUseCase authorizeDiscountUseCase;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private ThirdPartyRepository thirdPartyRepo;

    private UUID orderId;
    private UUID secondOrderId;
    private UUID adminUserId;
    private String adminUsername;
    private String dianPrefix;

    @BeforeEach
    void setUp() {
        var encodedPassword = passwordEncoder.encode(ADMIN_PASSWORD);
        // Unique per test: the shared container keeps invoices from other methods,
        // and the fiscal number is globally unique, so the prefix must not repeat.
        dianPrefix = "DA" + UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase();
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            adminUserId = UUID.randomUUID();
            adminUsername = "auth-admin-" + UUID.randomUUID().toString().substring(0, 8);
            em.createNativeQuery("""
                    INSERT INTO users (id, username, password_hash, email, full_name, role_id, is_active)
                    SELECT CAST(:id AS uuid), :username, :hash, :email, 'Authorization Admin', r.id, true
                    FROM roles r WHERE r.name = 'ADMIN'
                    """)
                    .setParameter("id", adminUserId.toString())
                    .setParameter("username", adminUsername)
                    .setParameter("hash", encodedPassword)
                    .setParameter("email", adminUsername + "@example.com")
                    .executeUpdate();

            var warehouseId = TestDataFactory.createWarehouse(
                    em, "DAU", Warehouse.WarehouseType.CORTES);

            var productId = TestDataFactory.createProduct(
                    em, "TEST-DAU0", "Discount Authorization Product", false, true);
            em.flush();
            TestDataFactory.setSalePriceAndTaxType(em, productId, PRICE, "EXENTO");

            var clientId = TestDataFactory.createClientThirdParty(
                    em, "DAU-" + UUID.randomUUID().toString().substring(0, 8), "Authorization Client");

            var systemSupplier = thirdPartyRepo.findByNumIdentification("000000000-0").orElseThrow();
            var batchId = TestDataFactory.createBatch(
                    em, productId, systemSupplier.id(), warehouseId,
                    new BigDecimal("100"), Batch.BatchStatus.OPEN);
            TestDataFactory.createStock(
                    em, productId, batchId, warehouseId, new BigDecimal("100"), BigDecimal.ZERO);

            TestDataFactory.createActiveDianResolution(
                    em, dianPrefix, 1L, 100_000L,
                    LocalDate.now().minusDays(1), LocalDate.now().plusDays(1));

            orderId = TestDataFactory.createConfirmedOrder(
                    em, "ORD-DAU-A-" + UUID.randomUUID().toString().substring(0, 6),
                    clientId, warehouseId,
                    List.of(new TestDataFactory.OrderLine(productId, batchId, BigDecimal.ONE)));
            secondOrderId = TestDataFactory.createConfirmedOrder(
                    em, "ORD-DAU-B-" + UUID.randomUUID().toString().substring(0, 6),
                    clientId, warehouseId,
                    List.of(new TestDataFactory.OrderLine(productId, batchId, BigDecimal.ONE)));
            em.flush();
        });
    }

    private CheckoutRequest checkoutRequest(UUID order, UUID authorizationId) {
        return new CheckoutRequest(
                order, null,
                List.of(new CheckoutRequest.PaymentLine("CASH", new BigDecimal("10000.00"))),
                "PCT", DISCOUNT_PCT, "Descuento autorizado por admin", authorizationId);
    }

    private SalesDocumentEntity loadInvoice(UUID order) {
        return new TransactionTemplate(transactionManager).execute(s -> em.createQuery(
                        "select d from SalesDocumentEntity d "
                                + "where d.type = :type and d.sourceDocumentId = :orderId",
                        SalesDocumentEntity.class)
                .setParameter("type", SalesDocumentType.INVOICE)
                .setParameter("orderId", order)
                .getSingleResult());
    }

    private static BusinessException businessError(ThrowingCallable call) {
        var thrown = catchThrowable(call);
        assertThat(thrown).isInstanceOf(BusinessException.class);
        return (BusinessException) thrown;
    }

    @Test
    void checkoutWithValidTokenStampsAuthorizedByOnTheInvoice() {
        var token = authorizeDiscountUseCase.authorize(adminUsername, ADMIN_PASSWORD, null, TEST_USER_ID);

        checkoutUseCase.checkout(checkoutRequest(orderId, token.authorizationId()), TEST_USER_ID);

        var invoice = loadInvoice(orderId);
        assertThat(invoice.getDiscountAuthorizedBy()).isEqualTo(adminUserId);
        assertThat(invoice.getDiscountReason()).isEqualTo("Descuento autorizado por admin");
        assertThat(invoice.getDiscountTotal()).isEqualByComparingTo("4000.00");
    }

    @Test
    void checkoutWithoutTokenRequiresAuthorization() {
        var error = businessError(() ->
                checkoutUseCase.checkout(checkoutRequest(orderId, null), TEST_USER_ID));

        assertThat(error.getErrorCode()).isEqualTo("DISCOUNT_AUTHORIZATION_REQUIRED");
    }

    @Test
    void reusingTheSameTokenFailsTheSecondCheckout() {
        var token = authorizeDiscountUseCase.authorize(adminUsername, ADMIN_PASSWORD, null, TEST_USER_ID);

        checkoutUseCase.checkout(checkoutRequest(orderId, token.authorizationId()), TEST_USER_ID);
        assertThat(loadInvoice(orderId).getDiscountAuthorizedBy()).isEqualTo(adminUserId);

        var error = businessError(() ->
                checkoutUseCase.checkout(checkoutRequest(secondOrderId, token.authorizationId()), TEST_USER_ID));
        assertThat(error.getErrorCode()).isEqualTo("DISCOUNT_AUTHORIZATION_REQUIRED");
    }
}
