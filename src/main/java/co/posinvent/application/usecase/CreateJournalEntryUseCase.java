package co.posinvent.application.usecase;

import co.posinvent.domain.model.JournalEntry;
import co.posinvent.domain.model.JournalEntryLine;
import co.posinvent.domain.repository.JournalEntryRepository;
import org.springframework.dao.DataIntegrityViolationException;
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

    /**
     * Creates an automatic journal entry for a business document.
     *
     * <p>Idempotency guard (WU-33): when the document carries a {@code sourceId}, the operation
     * is a no-op if an entry for the same {@code (sourceType, sourceId)} already exists — the
     * existing entry is returned instead of posting a second one. Re-publishing the same business
     * event therefore never duplicates the entry nor consumes a second numbering.</p>
     *
     * <p>A concurrent insert may still slip past the read-then-write check; the unique partial
     * index {@code uq_journal_entries_source} rejects it, and the resulting
     * {@link DataIntegrityViolationException} is translated back into the existing entry.</p>
     */
    @Transactional
    public JournalEntry createAuto(String sourceType, UUID sourceId, LocalDate entryDate,
                                     String description, List<JournalEntryLine> lines) {
        if (sourceId != null) {
            var existing = repo.findBySourceTypeAndSourceId(sourceType, sourceId);
            if (existing.isPresent()) {
                return existing.get();
            }
        }

        var entry = buildEntry(entryDate, description, sourceType, sourceId, lines);
        entry.validate();
        try {
            return repo.save(entry);
        } catch (DataIntegrityViolationException e) {
            // Lost the race against a concurrent post of the same source document: the unique
            // index did its job. Return the entry that won instead of failing the caller.
            if (sourceId != null) {
                return repo.findBySourceTypeAndSourceId(sourceType, sourceId).orElseThrow(() -> e);
            }
            throw e;
        }
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
