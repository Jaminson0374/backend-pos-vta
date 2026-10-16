package co.posinvent.integration;

import co.posinvent.application.dto.SupplierInvoiceRequest;
import co.posinvent.application.usecase.SupplierInvoiceUseCase;
import co.posinvent.domain.model.PurchaseRetentionConfig;
import co.posinvent.domain.model.ThirdParty;
import co.posinvent.domain.repository.PucAccountRepository;
import co.posinvent.domain.repository.PurchaseRetentionConfigRepository;
import co.posinvent.infrastructure.adapters.out.persistence.JournalEntryEntity;
import co.posinvent.infrastructure.adapters.out.persistence.JournalEntryLineEntity;
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

/**
 * Integration guard for data-driven purchase retentions (Contabilidad Fase 2, hallazgos A3/A4).
 *
 * <p>Retefuente is selected from the active {@code PurchaseRetentionConfig} catalog that matches the
 * supplier (regime/person type; null = wildcard) and whose {@code baseMin} is satisfied, applied on
 * the purchase base (subtotal without IVA). ICA is the empresa-level
 * {@code company_config.purchase_ica_rate} applied on the same base. The journal entry must credit
 * the retention accounts ({@code 2365} retefuente, {@code 2368} ICA) for the computed amounts.</p>
 *
 * <p>Deliberately NOT {@code @Transactional}: fixtures and the invoice are committed so the
 * {@code AFTER_COMMIT} accounting listener runs exactly like in production.</p>
 */
class PurchaseRetentionIntegrationTest extends AbstractIntegrationTest {

    private static final BigDecimal BASE = new BigDecimal("1000.00");
    private static final BigDecimal IVA = new BigDecimal("190.00");

    // Seeded standard config V90: RENTA_COMPRAS_BIENES = 2.50% on base ≥ 0.
    private static final BigDecimal EXPECTED_RETEFUENTE = new BigDecimal("25.00");
    // empresa-level ICA rate set per test (0.50%).
    private static final BigDecimal ICA_RATE = new BigDecimal("0.50");
    private static final BigDecimal EXPECTED_ICA = new BigDecimal("5.00");

    @Autowired private SupplierInvoiceUseCase supplierInvoiceUseCase;
    @Autowired private PucAccountRepository pucRepo;
    @Autowired private PurchaseRetentionConfigRepository retentionConfigRepository;

    private UUID supplierOrdinaryId;
    private UUID supplierSimpleId;

    @BeforeEach
    void setUp() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            supplierOrdinaryId = createSupplier(ThirdParty.TaxRegime.ORDINARIO);
            supplierSimpleId = createSupplier(ThirdParty.TaxRegime.SIMPLE);
            setIcaRate(BigDecimal.ZERO);
        });
    }

    @AfterEach
    void tearDown() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            em.createNativeQuery(
                    "DELETE FROM purchase_retention_configs WHERE code LIKE 'TEST_RET_%'")
                    .executeUpdate();
            setIcaRate(BigDecimal.ZERO);
        });
    }

    @Test
    void retefuenteIsCalculatedFromTheMatchingConfigAndCreditedTo2365() {
        // retentionTotal = null → computed data-driven (seeded 2.5% → 25.00).
        var total = BASE.add(IVA).subtract(EXPECTED_RETEFUENTE);
        var invoice = createInvoice(supplierOrdinaryId, total, null);

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            var entry = singlePurchaseEntry(invoice.id());
            assertThat(totalDebit(entry)).isEqualByComparingTo(totalCredit(entry));

            var retefuenteId = pucRepo.findByCode("2365").orElseThrow().id();
            assertThat(lineOn(entry, retefuenteId).getCredit()).isEqualByComparingTo(EXPECTED_RETEFUENTE);

            var icaId = pucRepo.findByCode("2368").orElseThrow().id();
            assertThat(entry.getLines()).noneMatch(l -> icaId.equals(l.getAccountId()));
        });
    }

    @Test
    void empresaIcaRateIsCreditedTo2368AlongsideRetefuente() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> setIcaRate(ICA_RATE));

        var retention = EXPECTED_RETEFUENTE.add(EXPECTED_ICA);
        var total = BASE.add(IVA).subtract(retention);
        var invoice = createInvoice(supplierOrdinaryId, total, null);

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            var entry = singlePurchaseEntry(invoice.id());
            assertThat(totalDebit(entry)).isEqualByComparingTo(totalCredit(entry));

            var retefuenteId = pucRepo.findByCode("2365").orElseThrow().id();
            var icaId = pucRepo.findByCode("2368").orElseThrow().id();
            assertThat(lineOn(entry, retefuenteId).getCredit()).isEqualByComparingTo(EXPECTED_RETEFUENTE);
            assertThat(lineOn(entry, icaId).getCredit()).isEqualByComparingTo(EXPECTED_ICA);
        });
    }

    @Test
    void configBelowBaseMinDoesNotApplyAndRetentionIsZero() {
        // A config that targets the supplier regime but whose baseMin (5000) is above the
        // invoice base (1000) must not apply → 0 retention.
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                retentionConfigRepository.save(new PurchaseRetentionConfig(
                        null, "TEST_RET_HIGH_BASE", "Test high base", null,
                        new BigDecimal("5.00"), new BigDecimal("5000.00"),
                        "SIMPLE", null, true, 0, null, null)));

        var total = BASE.add(IVA);
        var invoice = createInvoice(supplierSimpleId, total, null);

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            var entry = singlePurchaseEntry(invoice.id());
            assertThat(totalDebit(entry)).isEqualByComparingTo(totalCredit(entry));

            var retefuenteId = pucRepo.findByCode("2365").orElseThrow().id();
            var icaId = pucRepo.findByCode("2368").orElseThrow().id();
            assertThat(entry.getLines()).noneMatch(l -> retefuenteId.equals(l.getAccountId()));
            assertThat(entry.getLines()).noneMatch(l -> icaId.equals(l.getAccountId()));
        });
    }

    @Test
    void manualRetentionOverridesTheCalculationAndPostsAsRetefuente() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> setIcaRate(ICA_RATE));

        // Explicit manual retention bypasses the data-driven split and posts fully as retefuente.
        var manualRetention = new BigDecimal("100.00");
        var total = BASE.add(IVA).subtract(manualRetention);
        var invoice = createInvoice(supplierOrdinaryId, total, manualRetention);

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            var entry = singlePurchaseEntry(invoice.id());
            assertThat(totalDebit(entry)).isEqualByComparingTo(totalCredit(entry));

            var retefuenteId = pucRepo.findByCode("2365").orElseThrow().id();
            var icaId = pucRepo.findByCode("2368").orElseThrow().id();
            assertThat(lineOn(entry, retefuenteId).getCredit()).isEqualByComparingTo(manualRetention);
            assertThat(entry.getLines()).noneMatch(l -> icaId.equals(l.getAccountId()));
        });
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private UUID createSupplier(ThirdParty.TaxRegime regime) {
        var supplier = new ThirdPartyEntity();
        supplier.setNumIdentification("SUP-RET-" + UUID.randomUUID().toString().substring(0, 8));
        supplier.setName("Supplier Retention " + regime);
        supplier.setType(ThirdParty.ThirdPartyType.SUPPLIER);
        supplier.setPersonType(ThirdParty.PersonType.JURIDICA);
        supplier.setTaxRegime(regime);
        supplier.setTaxResponsibilities(List.of());
        supplier.setActive(true);
        supplier.setCreditLimit(BigDecimal.ZERO);
        supplier.setCurrentBalance(BigDecimal.ZERO);
        em.persist(supplier);
        return supplier.getId();
    }

    private void setIcaRate(BigDecimal rate) {
        em.createNativeQuery("UPDATE company_config SET purchase_ica_rate = :rate WHERE id = 1")
                .setParameter("rate", rate)
                .executeUpdate();
    }

    private co.posinvent.application.dto.SupplierInvoiceResponse createInvoice(
            UUID supplierId, BigDecimal total, BigDecimal manualRetention) {
        return supplierInvoiceUseCase.create(new SupplierInvoiceRequest(
                supplierId,
                "FAC-RET-" + UUID.randomUUID().toString().substring(0, 8),
                LocalDate.now(), LocalDate.now().plusDays(30),
                BASE, IVA, manualRetention, total,
                null, List.of()), TEST_USER_ID);
    }

    private JournalEntryEntity singlePurchaseEntry(UUID invoiceId) {
        var entries = em.createQuery(
                        "select e from JournalEntryEntity e "
                                + "where e.sourceType = 'PURCHASE' and e.sourceId = :sourceId",
                        JournalEntryEntity.class)
                .setParameter("sourceId", invoiceId)
                .getResultList();
        assertThat(entries).hasSize(1);
        return entries.get(0);
    }

    private static JournalEntryLineEntity lineOn(JournalEntryEntity entry, UUID accountId) {
        return entry.getLines().stream()
                .filter(l -> accountId.equals(l.getAccountId()))
                .findFirst().orElseThrow();
    }

    private static BigDecimal totalDebit(JournalEntryEntity entry) {
        return entry.getLines().stream()
                .map(JournalEntryLineEntity::getDebit)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal totalCredit(JournalEntryEntity entry) {
        return entry.getLines().stream()
                .map(JournalEntryLineEntity::getCredit)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
