package co.posinvent.infrastructure.adapters.out.persistence;

import co.posinvent.domain.model.JournalEntry;
import co.posinvent.domain.repository.JournalEntryRepository;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@Repository
class JournalEntryRepositoryAdapter implements JournalEntryRepository {
    private final JournalEntryJpaRepository jpa;
    private final JournalEntryMapper mapper;

    JournalEntryRepositoryAdapter(JournalEntryJpaRepository jpa, JournalEntryMapper mapper) {
        this.jpa = jpa; this.mapper = mapper;
    }

    @Override
    public JournalEntry save(JournalEntry entry) {
        var entity = mapper.toEntity(entry);
        if (entity.getLines() != null) {
            entity.getLines().forEach(line -> line.setEntry(entity));
        }
        return mapper.toDomain(jpa.save(entity));
    }

    @Override
    public Optional<JournalEntry> findById(UUID id) {
        return jpa.findById(id).map(mapper::toDomain);
    }

    @Override
    public Optional<JournalEntry> findBySourceTypeAndSourceId(String sourceType, UUID sourceId) {
        return jpa.findBySourceTypeAndSourceId(sourceType, sourceId).map(mapper::toDomain);
    }

    @Override
    public long nextEntrySequence() {
        return jpa.nextEntrySequence();
    }

    @Override
    public List<JournalEntry> findAllFiltered(String sourceType, LocalDate from, LocalDate to, int page, int size) {
        return jpa.findFiltered(sourceType, from, to).stream()
                .map(mapper::toDomain).toList();
    }

    @Override
    public List<JournalEntryLineDto> getLedger(UUID accountId, LocalDate from, LocalDate to) {
        return jpa.findLedgerRows(accountId, from, to).stream().map(row -> new JournalEntryLineDto(
                (UUID) row[0], (String) row[1],
                row[2] instanceof java.sql.Date d ? d.toLocalDate() : (LocalDate) row[2],
                (String) row[3], (UUID) row[4],
                (BigDecimal) row[5], (BigDecimal) row[6]
        )).toList();
    }

    @Override
    public List<TrialBalanceRow> getTrialBalance(LocalDate from, LocalDate to) {
        return jpa.findTrialBalance(from, to).stream().map(row -> new TrialBalanceRow(
                (String) row[0], (String) row[1],
                (BigDecimal) row[2], (BigDecimal) row[3]
        )).toList();
    }
}
