package co.posinvent.infrastructure.adapters.out.persistence;

import co.posinvent.domain.model.AccountsReceivable;
import co.posinvent.domain.repository.AccountsReceivableRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class AccountsReceivableRepositoryAdapter implements AccountsReceivableRepository {

    private final AccountsReceivableJpaRepository jpa;
    private final AccountsReceivableMapper mapper;

    public AccountsReceivableRepositoryAdapter(AccountsReceivableJpaRepository jpa,
                                                AccountsReceivableMapper mapper) {
        this.jpa = jpa;
        this.mapper = mapper;
    }

    @Override
    public AccountsReceivable save(AccountsReceivable ar) {
        return mapper.toDomain(jpa.save(mapper.toEntity(ar)));
    }

    @Override
    public Optional<AccountsReceivable> findById(UUID id) {
        return jpa.findById(id).map(mapper::toDomain);
    }

    @Override
    public Page<AccountsReceivable> findByClientId(UUID clientId, Pageable pageable) {
        return jpa.findByClientIdOrderByDueDateDesc(clientId, pageable).map(mapper::toDomain);
    }

    @Override
    public Page<AccountsReceivable> findByStatus(AccountsReceivable.ArStatus status, Pageable pageable) {
        return jpa.findByStatusOrderByDueDateDesc(status.name(), pageable).map(mapper::toDomain);
    }

    @Override
    public Page<AccountsReceivable> findByClientIdAndStatus(UUID clientId, AccountsReceivable.ArStatus status,
                                                             Pageable pageable) {
        return jpa.findByClientIdAndStatusOrderByDueDateDesc(clientId, status.name(), pageable)
                .map(mapper::toDomain);
    }

    @Override
    public List<AccountsReceivable> findOverdueBefore(LocalDate date) {
        return jpa.findByDueDateBeforeAndStatusNot(date, "PAID").stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public List<AccountsReceivable> findOverdueBeforeGrace(LocalDate today, int graceDays) {
        LocalDate cutoffDate = today.minusDays(graceDays);
        return jpa.findByDueDateBeforeAndStatusNot(cutoffDate, "PAID").stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public Optional<AccountsReceivable> findByDocumentId(UUID documentId) {
        return jpa.findByDocumentId(documentId).map(mapper::toDomain);
    }

    @Override
    public List<AccountsReceivable> findByStatusAndOutstandingGreaterThan(
            AccountsReceivable.ArStatus status, java.math.BigDecimal minOutstanding, LocalDate dueDateBefore) {
        return jpa.findByStatusAndOutstandingGreaterThanAndDueDateBefore(
                status.name(), minOutstanding, dueDateBefore)
                .stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public Page<AccountsReceivable> findAll(Pageable pageable) {
        return jpa.findAll(pageable).map(mapper::toDomain);
    }
}
