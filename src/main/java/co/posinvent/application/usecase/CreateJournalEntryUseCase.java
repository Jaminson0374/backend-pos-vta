package co.posinvent.application.usecase;

import co.posinvent.domain.model.JournalEntry;
import co.posinvent.domain.model.JournalEntryLine;
import co.posinvent.domain.repository.JournalEntryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Service
public class CreateJournalEntryUseCase {
    private final JournalEntryRepository repo;

    public CreateJournalEntryUseCase(JournalEntryRepository repo) { this.repo = repo; }

    @Transactional
    public JournalEntry createManual(LocalDate entryDate, String description, List<JournalEntryLine> lines) {
        var entry = buildEntry(entryDate, description, "MANUAL", null, lines);
        entry.validate();
        return repo.save(entry);
    }

    @Transactional
    public JournalEntry createAuto(String sourceType, UUID sourceId, LocalDate entryDate,
                                     String description, List<JournalEntryLine> lines) {
        var entry = buildEntry(entryDate, description, sourceType, sourceId, lines);
        entry.validate();
        return repo.save(entry);
    }

    public List<JournalEntry> list(String sourceType, LocalDate from, LocalDate to, int page, int size) {
        return repo.findAllFiltered(sourceType, from, to, page, size);
    }

    public List<JournalEntryRepository.JournalEntryLineDto> ledger(UUID accountId, LocalDate from, LocalDate to) {
        return repo.getLedger(accountId, from, to);
    }

    public List<JournalEntryRepository.TrialBalanceRow> trialBalance(LocalDate from, LocalDate to) {
        return repo.getTrialBalance(from, to);
    }

    private JournalEntry buildEntry(LocalDate date, String description, String sourceType, UUID sourceId,
                                      List<JournalEntryLine> lines) {
        String datePart = date.format(DateTimeFormatter.BASIC_ISO_DATE);
        long seq = repo.nextEntrySequence();
        return new JournalEntry(null, "JE-" + datePart + "-" + String.format("%06d", seq), date, description, sourceType, sourceId, null, lines);
    }
}
