package co.posinvent.integration;

import co.posinvent.application.dto.SupplierInvoiceRequest;
import co.posinvent.application.dto.SupplierInvoiceResponse;
import co.posinvent.application.usecase.SupplierInvoiceUseCase;
import co.posinvent.domain.exception.BusinessException;
import co.posinvent.domain.model.ThirdParty;
import co.posinvent.infrastructure.adapters.out.persistence.ThirdPartyEntity;
import org.junit.jupiter.api.AfterEach;
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
 * Integration guard for the manual-retention override role policy (Auditoría Compras, hallazgo A2).
 *
 * <p>The retention is 100% backend-owned: when {@code retentionTotal} is null the retefuente is
 * computed from the active {@code PurchaseRetentionConfig} catalog and the {@code total} is
 * recomputed server-side. A manual override is only allowed for {@code ADMIN} or {@code CONTADOR};
 * any other role attempting it must receive {@code RETENTION_OVERRIDE_FORBIDDEN}.</p>
 *
 * <p>Deliberately NOT {@code @Transactional}: fixtures and the invoice are committed so the
 * {@code AFTER_COMMIT} accounting listener runs exactly like in production.</p>
 */
class RetentionOverrideRoleIntegrationTest extends AbstractIntegrationTest {

    private static final BigDecimal BASE = new BigDecimal("1000.00");
    private static final BigDecimal IVA = new BigDecimal("190.00");

    // Seeded standard config V90: RENTA_COMPRAS_BIENES = 2.50% on a COMUN supplier base ≥ 0.
    private static final BigDecimal EXPECTED_RETEFUENTE = new BigDecimal("25.00");

    @Autowired private SupplierInvoiceUseCase supplierInvoiceUseCase;

    private UUID supplierId;
    private UUID auxiliarId;

    @BeforeEach
    void setUp() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            supplierId = createSupplier();
            setIcaRate(BigDecimal.ZERO);
            auxiliarId = createUser("AUXILIAR");
        });
    }

    @AfterEach
    void tearDown() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            em.createNativeQuery("DELETE FROM users WHERE username LIKE 'ret-role-%'")
                    .executeUpdate();
            setIcaRate(BigDecimal.ZERO);
        });
    }

    @Test
    void adminManualOverrideIsRespectedAndPostedAsRetefuente() {
        var override = new BigDecimal("100.00");
        var total = BASE.add(IVA).subtract(override);

        // TEST_USER_ID is seeded with the ADMIN role by AbstractIntegrationTest.
        var invoice = createInvoice(TEST_USER_ID, total, override);

        assertThat(invoice.retentionTotal()).isEqualByComparingTo(override);
        assertThat(invoice.total()).isEqualByComparingTo(total);
    }

    @Test
    void auxiliarManualOverrideIsRejected() {
        var override = new BigDecimal("100.00");
        var total = BASE.add(IVA).subtract(override);

        assertThatThrownBy(() -> createInvoice(auxiliarId, total, override))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", "RETENTION_OVERRIDE_FORBIDDEN");
    }

    @Test
    void automaticRetentionRecomputesTotalIgnoringTheClientsTotal() {
        // The client sends an inconsistent total (retention not applied); the backend owns the
        // calculation and must recompute total = subtotal + IVA - retention.
        var inconsistentTotal = BASE.add(IVA);

        var invoice = createInvoice(TEST_USER_ID, inconsistentTotal, null);

        assertThat(invoice.retentionTotal()).isEqualByComparingTo(EXPECTED_RETEFUENTE);
        assertThat(invoice.total())
                .isEqualByComparingTo(BASE.add(IVA).subtract(EXPECTED_RETEFUENTE));
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private SupplierInvoiceResponse createInvoice(
            UUID operatorId, BigDecimal total, BigDecimal manualRetention) {
        return supplierInvoiceUseCase.create(new SupplierInvoiceRequest(
                supplierId,
                "FAC-RET-ROLE-" + UUID.randomUUID().toString().substring(0, 8),
                LocalDate.now(), LocalDate.now().plusDays(30),
                BASE, IVA, manualRetention, total,
                null, List.of()), operatorId);
    }

    private UUID createSupplier() {
        var supplier = new ThirdPartyEntity();
        supplier.setNumIdentification("SUP-RETROLE-" + UUID.randomUUID().toString().substring(0, 8));
        supplier.setName("Supplier Retention Role");
        supplier.setType(ThirdParty.ThirdPartyType.SUPPLIER);
        supplier.setPersonType(ThirdParty.PersonType.JURIDICA);
        supplier.setTaxRegime(ThirdParty.TaxRegime.ORDINARIO);
        supplier.setTaxResponsibilities(List.of());
        supplier.setActive(true);
        supplier.setCreditLimit(BigDecimal.ZERO);
        supplier.setCurrentBalance(BigDecimal.ZERO);
        em.persist(supplier);
        return supplier.getId();
    }

    private UUID createUser(String roleName) {
        var id = UUID.randomUUID();
        var username = "ret-role-" + id.toString().substring(0, 8);
        em.createNativeQuery("""
                INSERT INTO users (id, username, password_hash, email, full_name, role_id, is_active)
                SELECT CAST(:id AS uuid), :username, 'n/a', :email, 'Retention Role User', r.id, true
                FROM roles r WHERE r.name = :role
                """)
                .setParameter("id", id.toString())
                .setParameter("username", username)
                .setParameter("email", username + "@example.com")
                .setParameter("role", roleName)
                .executeUpdate();
        return id;
    }

    private void setIcaRate(BigDecimal rate) {
        em.createNativeQuery("UPDATE company_config SET purchase_ica_rate = :rate WHERE id = 1")
                .setParameter("rate", rate)
                .executeUpdate();
    }
}
