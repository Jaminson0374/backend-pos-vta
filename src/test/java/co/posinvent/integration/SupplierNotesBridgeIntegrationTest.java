package co.posinvent.integration;

import co.posinvent.application.dto.DebitCreditNoteRequest;
import co.posinvent.application.usecase.ManageDebitCreditNoteUseCase;
import co.posinvent.domain.model.ThirdParty;
import co.posinvent.domain.repository.PucAccountRepository;
import co.posinvent.infrastructure.adapters.out.persistence.JournalEntryEntity;
import co.posinvent.infrastructure.adapters.out.persistence.JournalEntryLineEntity;
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
 * Integration guard for supplier debit/credit note accounting (Contabilidad Fase 2, WU-36b, C5).
 *
 * <p>A credit note (the supplier credits us) posts debit proveedores {@code 2205} / credit
 * puente {@code 220510}; a debit note posts the mirror entry. Both are dated with the note and
 * keyed by the note id under {@code PURCHASE}.</p>
 *
 * <p>Deliberately NOT {@code @Transactional} so the {@code AFTER_COMMIT} listener runs.</p>
 */
class SupplierNotesBridgeIntegrationTest extends AbstractIntegrationTest {

    private static final BigDecimal AMOUNT = new BigDecimal("500.00");

    @Autowired private ManageDebitCreditNoteUseCase manageDebitCreditNoteUseCase;
    @Autowired private PucAccountRepository pucRepo;

    private UUID supplierId;

    @BeforeEach
    void setUp() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            var supplier = new ThirdPartyEntity();
            supplier.setNumIdentification("SUP-NOTE-" + UUID.randomUUID().toString().substring(0, 8));
            supplier.setName("Supplier Note WU36b");
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
    void creditNoteDebitsPayableAndCreditsTheBridge() {
        var note = manageDebitCreditNoteUseCase.create(new DebitCreditNoteRequest(
                "CREDIT_NOTE", supplierId, null, null, AMOUNT, "Nota crédito prueba", null));

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            var payableId = pucRepo.findByCode("2205").orElseThrow().id();
            var bridgeId = pucRepo.findByCode("220510").orElseThrow().id();

            var entries = entriesFor("PURCHASE", note.id());
            assertThat(entries).hasSize(1);
            var entry = entries.get(0);

            assertThat(entry.getEntryDate()).isEqualTo(LocalDate.now());
            assertThat(totalDebit(entry)).isEqualByComparingTo(totalCredit(entry));
            assertThat(lineOn(entry, payableId).getDebit()).isEqualByComparingTo(AMOUNT);
            assertThat(lineOn(entry, bridgeId).getCredit()).isEqualByComparingTo(AMOUNT);
        });
    }

    @Test
    void debitNoteDebitsTheBridgeAndCreditsPayable() {
        var note = manageDebitCreditNoteUseCase.create(new DebitCreditNoteRequest(
                "DEBIT_NOTE", supplierId, null, null, AMOUNT, "Nota débito prueba", null));

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            var payableId = pucRepo.findByCode("2205").orElseThrow().id();
            var bridgeId = pucRepo.findByCode("220510").orElseThrow().id();

            var entries = entriesFor("PURCHASE", note.id());
            assertThat(entries).hasSize(1);
            var entry = entries.get(0);

            assertThat(totalDebit(entry)).isEqualByComparingTo(totalCredit(entry));
            assertThat(lineOn(entry, bridgeId).getDebit()).isEqualByComparingTo(AMOUNT);
            assertThat(lineOn(entry, payableId).getCredit()).isEqualByComparingTo(AMOUNT);
        });
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
