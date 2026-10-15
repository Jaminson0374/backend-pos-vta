package co.posinvent.application.usecase;

import co.posinvent.domain.model.JournalEntry;
import co.posinvent.domain.model.JournalEntryLine;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreateJournalEntryUseCaseTest {

    private static final LocalDate ENTRY_DATE = LocalDate.of(2026, 3, 14);
    private static final String DATE_PART = ENTRY_DATE.format(DateTimeFormatter.BASIC_ISO_DATE);
    private static final UUID DEBIT_ACCOUNT = UUID.randomUUID();
    private static final UUID CREDIT_ACCOUNT = UUID.randomUUID();

    @Mock private JournalEntryRepository repo;

    private CreateJournalEntryUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new CreateJournalEntryUseCase(repo);
        when(repo.save(any(JournalEntry.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void createManual_usesSequenceFromRepository() {
        when(repo.nextEntrySequence()).thenReturn(41L, 42L);

        JournalEntry first = useCase.createManual(ENTRY_DATE, "first", balancedLines());
        JournalEntry second = useCase.createManual(ENTRY_DATE, "second", balancedLines());

        assertThat(first.entryNumber()).isEqualTo("JE-" + DATE_PART + "-000041");
        assertThat(second.entryNumber()).isEqualTo("JE-" + DATE_PART + "-000042");

        verify(repo, times(2)).nextEntrySequence();
    }

    @Test
    void createManual_producesDistinctNumbersAndKeepsShape() {
        when(repo.nextEntrySequence()).thenReturn(7L, 8L);

        JournalEntry first = useCase.createManual(ENTRY_DATE, "first", balancedLines());
        JournalEntry second = useCase.createManual(ENTRY_DATE, "second", balancedLines());

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

    private List<JournalEntryLine> balancedLines() {
        return List.of(
                new JournalEntryLine(null, null, DEBIT_ACCOUNT, new BigDecimal("50.00"), BigDecimal.ZERO, "debit"),
                new JournalEntryLine(null, null, CREDIT_ACCOUNT, BigDecimal.ZERO, new BigDecimal("50.00"), "credit"));
    }
}
