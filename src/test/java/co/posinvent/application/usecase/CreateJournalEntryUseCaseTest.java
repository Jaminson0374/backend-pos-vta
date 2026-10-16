package co.posinvent.application.usecase;

import co.posinvent.domain.exception.BusinessException;
import co.posinvent.domain.model.JournalEntry;
import co.posinvent.domain.model.JournalEntryLine;
import co.posinvent.domain.repository.CurrentUserProvider;
import co.posinvent.domain.repository.JournalEntryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreateJournalEntryUseCaseTest {

    private static final LocalDate ENTRY_DATE = LocalDate.of(2026, 3, 14);
    private static final String DATE_PART = ENTRY_DATE.format(DateTimeFormatter.BASIC_ISO_DATE);
    private static final UUID DEBIT_ACCOUNT = UUID.randomUUID();
    private static final UUID CREDIT_ACCOUNT = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();

    @Mock private JournalEntryRepository repo;
    @Mock private AccountingPeriodUseCase accountingPeriodUseCase;
    @Mock private CurrentUserProvider currentUserProvider;

    private CreateJournalEntryUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new CreateJournalEntryUseCase(repo, accountingPeriodUseCase, currentUserProvider);
        lenient().when(repo.save(any(JournalEntry.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(currentUserProvider.currentUserId()).thenReturn(Optional.empty());
    }

    @Test
    void createManual_usesSequenceFromRepository() {
        when(repo.nextEntrySequence()).thenReturn(41L, 42L);

        JournalEntry first = useCase.createManual(ENTRY_DATE, "first", "justificación de prueba", balancedLines());
        JournalEntry second = useCase.createManual(ENTRY_DATE, "second", "justificación de prueba", balancedLines());

        assertThat(first.entryNumber()).isEqualTo("JE-" + DATE_PART + "-000041");
        assertThat(second.entryNumber()).isEqualTo("JE-" + DATE_PART + "-000042");

        verify(repo, times(2)).nextEntrySequence();
    }

    @Test
    void createManual_producesDistinctNumbersAndKeepsShape() {
        when(repo.nextEntrySequence()).thenReturn(7L, 8L);

        JournalEntry first = useCase.createManual(ENTRY_DATE, "first", "justificación de prueba", balancedLines());
        JournalEntry second = useCase.createManual(ENTRY_DATE, "second", "justificación de prueba", balancedLines());

        assertThat(first.entryNumber()).isNotEqualTo(second.entryNumber());
        assertThat(first.entryNumber()).startsWith("JE-" + DATE_PART + "-");
        assertThat(second.entryNumber()).startsWith("JE-" + DATE_PART + "-");
        assertThat(first.entryNumber()).hasSize("JE-".length() + DATE_PART.length() + 1 + 6);
    }

    @Test
    void createAuto_usesSequenceFromRepository() {
        when(repo.nextEntrySequence()).thenReturn(100L);
        UUID sourceId = UUID.randomUUID();

        JournalEntry entry = useCase.createAuto("SALE", sourceId, ENTRY_DATE, "auto", balancedLines());

        assertThat(entry.entryNumber()).isEqualTo("JE-" + DATE_PART + "-000100");
        assertThat(entry.sourceType()).isEqualTo("SALE");
        assertThat(entry.sourceId()).isEqualTo(sourceId);
        verify(repo).nextEntrySequence();
    }

    @Test
    void createAuto_returnsExistingEntryWhenSourceWasAlreadyPosted() {
        UUID sourceId = UUID.randomUUID();
        var existing = new JournalEntry(UUID.randomUUID(), "JE-" + DATE_PART + "-000001",
                ENTRY_DATE, "existing", "SALE", sourceId, null, null, null, balancedLines());
        when(repo.findBySourceTypeAndSourceId("SALE", sourceId)).thenReturn(Optional.of(existing));

        JournalEntry result = useCase.createAuto("SALE", sourceId, ENTRY_DATE, "duplicate", balancedLines());

        assertThat(result).isSameAs(existing);
        verify(repo, never()).save(any(JournalEntry.class));
        verify(repo, never()).nextEntrySequence();
    }

    @Test
    void createAuto_postsOnceForSameSourceAcrossTwoCalls() {
        UUID sourceId = UUID.randomUUID();
        var existing = new JournalEntry(UUID.randomUUID(), "JE-" + DATE_PART + "-000100",
                ENTRY_DATE, "first", "SALE", sourceId, null, null, null, balancedLines());
        when(repo.nextEntrySequence()).thenReturn(100L);
        when(repo.findBySourceTypeAndSourceId("SALE", sourceId))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(existing));

        JournalEntry first = useCase.createAuto("SALE", sourceId, ENTRY_DATE, "first", balancedLines());
        JournalEntry second = useCase.createAuto("SALE", sourceId, ENTRY_DATE, "second", balancedLines());

        assertThat(first.entryNumber()).isEqualTo("JE-" + DATE_PART + "-000100");
        assertThat(second).isSameAs(existing);

        // A single entry was persisted and a single numbering was consumed.
        verify(repo, times(1)).save(any(JournalEntry.class));
        verify(repo, times(1)).nextEntrySequence();
    }

    @Test
    void createAuto_withoutSourceIdIsNeverGuarded() {
        when(repo.nextEntrySequence()).thenReturn(5L);

        JournalEntry entry = useCase.createAuto("MANUAL", null, ENTRY_DATE, "manual", balancedLines());

        assertThat(entry.entryNumber()).isEqualTo("JE-" + DATE_PART + "-000005");
        assertThat(entry.sourceId()).isNull();
        verify(repo, never()).findBySourceTypeAndSourceId(anyString(), any());
        verify(repo).save(any(JournalEntry.class));
    }

    @Test
    void createManual_isNeverGuardedBySource() {
        when(repo.nextEntrySequence()).thenReturn(1L, 2L);

        useCase.createManual(ENTRY_DATE, "first", "justificación de prueba", balancedLines());
        useCase.createManual(ENTRY_DATE, "second", "justificación de prueba", balancedLines());

        verify(repo, times(2)).save(any(JournalEntry.class));
        verify(repo, never()).findBySourceTypeAndSourceId(anyString(), any());
    }

    @Test
    void createManual_setsCreatedByFromAuthenticatedUser() {
        when(repo.nextEntrySequence()).thenReturn(9L);
        when(currentUserProvider.currentUserId()).thenReturn(Optional.of(USER_ID));

        JournalEntry entry = useCase.createManual(ENTRY_DATE, "manual", "Ajuste de cierre", balancedLines());

        assertThat(entry.createdBy()).isEqualTo(USER_ID);
        verify(accountingPeriodUseCase).assertOpen(ENTRY_DATE);
    }

    @Test
    void createManual_persistsJustificationAndSourceType() {
        when(repo.nextEntrySequence()).thenReturn(11L);
        when(currentUserProvider.currentUserId()).thenReturn(Optional.of(USER_ID));

        JournalEntry entry = useCase.createManual(ENTRY_DATE, "manual", "Ajuste de cierre", balancedLines());

        assertThat(entry.justification()).isEqualTo("Ajuste de cierre");
        assertThat(entry.sourceType()).isEqualTo("MANUAL");
        assertThat(entry.createdBy()).isEqualTo(USER_ID);
    }

    @Test
    void createAuto_doesNotCarryJustification() {
        when(repo.nextEntrySequence()).thenReturn(77L);

        JournalEntry entry = useCase.createAuto("SALE", UUID.randomUUID(), ENTRY_DATE, "auto", balancedLines());

        assertThat(entry.justification()).isNull();
    }

    @Test
    void createManual_rejectsEntryInClosedPeriod() {
        doThrow(new BusinessException("ACCOUNTING_PERIOD_CLOSED", "El período contable 2026-03 está cerrado"))
                .when(accountingPeriodUseCase).assertOpen(ENTRY_DATE);

        assertThatThrownBy(() -> useCase.createManual(ENTRY_DATE, "closed", "justificación de prueba", balancedLines()))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo("ACCOUNTING_PERIOD_CLOSED");

        verify(repo, never()).save(any(JournalEntry.class));
        verify(repo, never()).nextEntrySequence();
    }

    @Test
    void createAuto_rejectsNewEntryInClosedPeriod() {
        UUID sourceId = UUID.randomUUID();
        when(repo.findBySourceTypeAndSourceId("SALE", sourceId)).thenReturn(Optional.empty());
        doThrow(new BusinessException("ACCOUNTING_PERIOD_CLOSED", "El período contable 2026-03 está cerrado"))
                .when(accountingPeriodUseCase).assertOpen(ENTRY_DATE);

        assertThatThrownBy(() -> useCase.createAuto("SALE", sourceId, ENTRY_DATE, "closed", balancedLines()))
                .isInstanceOf(BusinessException.class);

        verify(repo, never()).save(any(JournalEntry.class));
    }

    @Test
    void createAuto_returnsExistingEntryWithoutCheckingPeriodOnRetry() {
        UUID sourceId = UUID.randomUUID();
        var existing = new JournalEntry(UUID.randomUUID(), "JE-" + DATE_PART + "-000001",
                ENTRY_DATE, "existing", "SALE", sourceId, null, null, null, balancedLines());
        when(repo.findBySourceTypeAndSourceId("SALE", sourceId)).thenReturn(Optional.of(existing));

        JournalEntry result = useCase.createAuto("SALE", sourceId, ENTRY_DATE, "retry", balancedLines());

        assertThat(result).isSameAs(existing);
        verify(accountingPeriodUseCase, never()).assertOpen(any());
    }

    private List<JournalEntryLine> balancedLines() {
        return List.of(
                new JournalEntryLine(null, null, DEBIT_ACCOUNT, new BigDecimal("50.00"), BigDecimal.ZERO, "debit"),
                new JournalEntryLine(null, null, CREDIT_ACCOUNT, BigDecimal.ZERO, new BigDecimal("50.00"), "credit"));
    }
}
