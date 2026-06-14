package co.posinvent.infrastructure.adapters.out.persistence;

import co.posinvent.domain.model.AccountsPayable;
import co.posinvent.domain.repository.AccountsPayableRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class AccountsPayableRepositoryAdapter implements AccountsPayableRepository {

    private final AccountsPayableJpaRepository jpa;
    private final AccountsPayableMapper mapper;

    public AccountsPayableRepositoryAdapter(AccountsPayableJpaRepository jpa,
                                             AccountsPayableMapper mapper) {
        this.jpa = jpa;
        this.mapper = mapper;
    }

    @Override
    public AccountsPayable save(AccountsPayable ap) {
        return mapper.toDomain(jpa.save(mapper.toEntity(ap)));
    }

    @Override
    public Optional<AccountsPayable> findById(UUID id) {
        return jpa.findById(id).map(mapper::toDomain);
    }

    @Override
    public Page<AccountsPayable> findBySupplierId(UUID supplierId, Pageable pageable) {
        return jpa.findBySupplierIdOrderByDueDateDesc(supplierId, pageable).map(mapper::toDomain);
    }

    @Override
    public Page<AccountsPayable> findByStatus(AccountsPayable.ApStatus status, Pageable pageable) {
        return jpa.findByStatusOrderByDueDateDesc(status.name(), pageable).map(mapper::toDomain);
    }

    @Override
    public Page<AccountsPayable> findBySupplierIdAndStatus(UUID supplierId, AccountsPayable.ApStatus status,
                                                            Pageable pageable) {
        return jpa.findBySupplierIdAndStatusOrderByDueDateDesc(supplierId, status.name(), pageable)
                .map(mapper::toDomain);
    }

    @Override
    public List<AccountsPayable> findOverdueBefore(LocalDate date) {
        return jpa.findByDueDateBeforeAndStatusNot(date, "PAID").stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public Optional<AccountsPayable> findByDocumentId(UUID documentId) {
        return jpa.findByDocumentId(documentId).map(mapper::toDomain);
    }

    @Override
    public Page<AccountsPayable> findAll(Pageable pageable) {
        return jpa.findAll(pageable).map(mapper::toDomain);
    }
}
