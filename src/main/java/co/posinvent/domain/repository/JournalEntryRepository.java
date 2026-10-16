package co.posinvent.domain.repository;

import co.posinvent.domain.model.JournalEntry;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JournalEntryRepository {
    JournalEntry save(JournalEntry entry);
    Optional<JournalEntry> findById(UUID id);
    Optional<JournalEntry> findBySourceTypeAndSourceId(String sourceType, UUID sourceId);
    long nextEntrySequence();
    List<JournalEntry> findAllFiltered(String sourceType, LocalDate from, LocalDate to, int page, int size);
    List<JournalEntryLineDto> getLedger(UUID accountId, LocalDate from, LocalDate to);
    List<TrialBalanceRow> getTrialBalance(LocalDate from, LocalDate to);

    record JournalEntryLineDto(UUID entryId, String entryNumber, LocalDate entryDate, String description, UUID accountId, java.math.BigDecimal debit, java.math.BigDecimal credit) {}
    record TrialBalanceRow(String accountCode, String accountName, java.math.BigDecimal totalDebit, java.math.BigDecimal totalCredit) {}
}
