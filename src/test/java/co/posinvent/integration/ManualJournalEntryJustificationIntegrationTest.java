package co.posinvent.integration;

import co.posinvent.application.usecase.CreateJournalEntryUseCase;
import co.posinvent.domain.model.JournalEntry;
import co.posinvent.domain.model.JournalEntryLine;
import co.posinvent.domain.repository.PucAccountRepository;
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
 * Integration test for justified manual journal entries (Contabilidad Fase 2 — A8).
 *
 * <p>A manual entry is posted through {@link CreateJournalEntryUseCase#createManual} with a
 * mandatory justification. The persisted row must keep that justification, flag itself with
 * {@code source_type = MANUAL}, and record the authenticated author in {@code created_by}.</p>
 */
class ManualJournalEntryJustificationIntegrationTest extends AbstractIntegrationTest {

    private static final BigDecimal AMOUNT = new BigDecimal("123.45");
    private static final String JUSTIFICATION = "Ajuste de cierre mensual";

    @Autowired private CreateJournalEntryUseCase journalUseCase;
    @Autowired private PucAccountRepository pucRepo;

    private UUID debitAccountId;
    private UUID creditAccountId;

    @BeforeEach
    void setUp() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            debitAccountId = pucRepo.findByCode("1305").orElseThrow().id();
            creditAccountId = pucRepo.findByCode("4135").orElseThrow().id();
        });
    }

    @Test
    void createManualPersistsJustificationAndAuthor() {
        JournalEntry created = journalUseCase.createManual(
                LocalDate.now(), "Cierre de mes", JUSTIFICATION, balancedLines());

        assertThat(created.justification()).isEqualTo(JUSTIFICATION);
        assertThat(created.createdBy()).isEqualTo(TEST_USER_ID);
        assertThat(created.sourceType()).isEqualTo("MANUAL");

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            var persisted = em.createQuery(
                            "select e from JournalEntryEntity e where e.id = :id",
                            co.posinvent.infrastructure.adapters.out.persistence.JournalEntryEntity.class)
                    .setParameter("id", created.id())
                    .getSingleResult();

            assertThat(persisted.getJustification()).isEqualTo(JUSTIFICATION);
            assertThat(persisted.getCreatedBy()).isEqualTo(TEST_USER_ID);
            assertThat(persisted.getSourceType()).isEqualTo("MANUAL");
        });
    }

    private List<JournalEntryLine> balancedLines() {
        return List.of(
                new JournalEntryLine(null, null, debitAccountId, AMOUNT, BigDecimal.ZERO, "debit"),
                new JournalEntryLine(null, null, creditAccountId, BigDecimal.ZERO, AMOUNT, "credit"));
    }
}
