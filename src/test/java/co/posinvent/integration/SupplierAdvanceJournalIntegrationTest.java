package co.posinvent.integration;

import co.posinvent.application.dto.AdvanceRequest;
import co.posinvent.application.dto.ApplyAdvanceRequest;
import co.posinvent.application.usecase.ApplyAdvanceUseCase;
import co.posinvent.application.usecase.PaymentUseCase;
import co.posinvent.domain.model.InvoiceStatus;
import co.posinvent.domain.model.ThirdParty;
import co.posinvent.domain.repository.AdvanceApplicationRepository;
import co.posinvent.domain.repository.PucAccountRepository;
import co.posinvent.infrastructure.adapters.out.persistence.JournalEntryEntity;
import co.posinvent.infrastructure.adapters.out.persistence.JournalEntryLineEntity;
import co.posinvent.infrastructure.adapters.out.persistence.SupplierInvoiceEntity;
import co.posinvent.infrastructure.adapters.out.persistence.ThirdPartyEntity;
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
 * Integration guard for supplier-advance accounting (S3b).
 *
 * <p>Creating an unapplied advance is cash out that becomes an asset: debit {@code 133005}
 * (anticipos a proveedores) / credit {@code 1105} (caja). Applying it against a supplier invoice
 * crosses the asset against the payable: debit {@code 2205} / credit {@code 133005}. Neither path
 * posts a regular {@code PAYMENT} entry.</p>
 *
 * <p>Deliberately NOT {@code @Transactional}: fixtures and use cases commit their own transactions
 * so the {@code AFTER_COMMIT} accounting listener runs exactly like in production, leaving the
 * journal entry readable afterwards.</p>
 */
class SupplierAdvanceJournalIntegrationTest extends AbstractIntegrationTest {

    private static final BigDecimal ADVANCE_AMOUNT = new BigDecimal("1000.00");
    private static final BigDecimal INVOICE_AMOUNT = new BigDecimal("1000.00");
    private static final BigDecimal APPLIED_AMOUNT = new BigDecimal("600.00");
    private static final LocalDate ADVANCE_DATE = LocalDate.of(2024, 5, 10);

    @Autowired private PaymentUseCase paymentUseCase;
    @Autowired private ApplyAdvanceUseCase applyAdvanceUseCase;
    @Autowired private AdvanceApplicationRepository advanceApplicationRepository;
    @Autowired private PucAccountRepository pucRepo;

    private UUID supplierId;

    @BeforeEach
    void setUp() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            var supplier = new ThirdPartyEntity();
            supplier.setNumIdentification("SUP-ADV-JRN-" + UUID.randomUUID().toString().substring(0, 8));
            supplier.setName("Supplier Advance Journal");
            supplier.setType(ThirdParty.ThirdPartyType.SUPPLIER);
            supplier.setPersonType(ThirdParty.PersonType.JURIDICA);
            supplier.setTaxRegime(ThirdParty.TaxRegime.ORDINARIO);
            supplier.setTaxResponsibilities(List.of());
            supplier.setActive(true);
            supplier.setCreditLimit(BigDecimal.ZERO);
            supplier.setCurrentBalance(BigDecimal.ZERO);
            em.persist(supplier);
            supplierId = supplier.getId();
        });
    }

    @Test
    void creatingAnAdvancePostsAssetDebitCashCreditAndNoPaymentEntry() {
        var advanceId = advance();

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            var assetId = pucRepo.findByCode("133005").orElseThrow().id();
            var cashId = pucRepo.findByCode("1105").orElseThrow().id();

            var entries = entriesFor("ADVANCE", advanceId);
            assertThat(entries).hasSize(1);
            var entry = entries.get(0);

            assertThat(entry.getEntryDate()).isEqualTo(ADVANCE_DATE);
            assertThat(totalDebit(entry)).isEqualByComparingTo(totalCredit(entry));
            assertThat(totalDebit(entry)).isEqualByComparingTo(ADVANCE_AMOUNT);

            var asset = lineOn(entry, assetId);
            assertThat(asset.getDebit()).isEqualByComparingTo(ADVANCE_AMOUNT);
            assertThat(asset.getCredit()).isEqualByComparingTo(BigDecimal.ZERO);

            var cash = lineOn(entry, cashId);
            assertThat(cash.getCredit()).isEqualByComparingTo(ADVANCE_AMOUNT);
            assertThat(cash.getDebit()).isEqualByComparingTo(BigDecimal.ZERO);

            // An advance is not a regular supplier payment.
            assertThat(entriesFor("PAYMENT", advanceId)).isEmpty();
        });
    }

    @Test
    void applyingPartOfTheAdvanceCrossesTheAssetAgainstThePayable() {
        var advanceId = advance();
        var creationEntryId = new TransactionTemplate(transactionManager).execute(status ->
                entriesFor("ADVANCE", advanceId).get(0).getId());
        var invoiceId = pendingInvoice();

        applyAdvanceUseCase.apply(
                new ApplyAdvanceRequest(advanceId, invoiceId, APPLIED_AMOUNT), TEST_USER_ID);

        var application = advanceApplicationRepository.findByAdvancePaymentId(advanceId)
                .stream().findFirst().orElseThrow();

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            var payableId = pucRepo.findByCode("2205").orElseThrow().id();
            var assetId = pucRepo.findByCode("133005").orElseThrow().id();

            var applications = entriesFor("ADVANCE_APPLICATION", application.id());
            assertThat(applications).hasSize(1);
            var entry = applications.get(0);

            assertThat(entry.getEntryDate()).isEqualTo(application.applicationDate());
            assertThat(totalDebit(entry)).isEqualByComparingTo(totalCredit(entry));
            assertThat(totalDebit(entry)).isEqualByComparingTo(APPLIED_AMOUNT);

            var payable = lineOn(entry, payableId);
            assertThat(payable.getDebit()).isEqualByComparingTo(APPLIED_AMOUNT);
            assertThat(payable.getCredit()).isEqualByComparingTo(BigDecimal.ZERO);

            var asset = lineOn(entry, assetId);
            assertThat(asset.getCredit()).isEqualByComparingTo(APPLIED_AMOUNT);
            assertThat(asset.getDebit()).isEqualByComparingTo(BigDecimal.ZERO);

            // The creation entry is untouched: same row, still 1000 on both sides.
            var created = entriesFor("ADVANCE", advanceId);
            assertThat(created).hasSize(1);
            assertThat(created.get(0).getId()).isEqualTo(creationEntryId);
            assertThat(totalDebit(created.get(0))).isEqualByComparingTo(ADVANCE_AMOUNT);
            assertThat(totalCredit(created.get(0))).isEqualByComparingTo(ADVANCE_AMOUNT);
        });
    }

    // ── fixtures ─────────────────────────────────────────────────────────────

    private UUID advance() {
        return paymentUseCase.createAdvance(new AdvanceRequest(
                supplierId, ADVANCE_AMOUNT, ADVANCE_DATE, "TRANSFERENCIA",
                "ANT-JRN-" + UUID.randomUUID().toString().substring(0, 6), null),
                TEST_USER_ID).id();
    }

    private UUID pendingInvoice() {
        var holder = new UUID[1];
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            var invoice = new SupplierInvoiceEntity();
            invoice.setId(UUID.randomUUID());
            invoice.setSupplierId(supplierId);
            invoice.setInvoiceNumber("FAC-ADV-JRN-" + UUID.randomUUID().toString().substring(0, 8));
            invoice.setIssueDate(LocalDate.now().minusDays(5));
            invoice.setDueDate(LocalDate.now().plusDays(25));
            invoice.setSubtotal(INVOICE_AMOUNT);
            invoice.setIvaTotal(BigDecimal.ZERO);
            invoice.setRetentionTotal(BigDecimal.ZERO);
            invoice.setTotal(INVOICE_AMOUNT);
            invoice.setStatus(InvoiceStatus.PENDING);
            invoice.setCreatedBy(TEST_USER_ID);
            em.persist(invoice);
            em.flush();
            holder[0] = invoice.getId();
        });
        return holder[0];
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private List<JournalEntryEntity> entriesFor(String sourceType, UUID sourceId) {
        return em.createQuery(
                        "select e from JournalEntryEntity e "
                                + "where e.sourceType = :sourceType and e.sourceId = :sourceId",
                        JournalEntryEntity.class)
                .setParameter("sourceType", sourceType)
                .setParameter("sourceId", sourceId)
                .getResultList();
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
