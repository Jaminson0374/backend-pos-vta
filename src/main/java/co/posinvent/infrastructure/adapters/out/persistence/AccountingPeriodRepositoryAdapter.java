package co.posinvent.infrastructure.adapters.out.persistence;

import co.posinvent.domain.model.AccountingPeriod;
import co.posinvent.domain.repository.AccountingPeriodRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
class AccountingPeriodRepositoryAdapter implements AccountingPeriodRepository {

    private final AccountingPeriodJpaRepository jpa;
    private final AccountingPeriodMapper mapper;

    AccountingPeriodRepositoryAdapter(AccountingPeriodJpaRepository jpa, AccountingPeriodMapper mapper) {
        this.jpa = jpa;
        this.mapper = mapper;
    }

    @Override
    public Optional<AccountingPeriod> findById(String period) {
        return jpa.findById(period).map(mapper::toDomain);
    }

    @Override
    public AccountingPeriod save(AccountingPeriod period) {
        return mapper.toDomain(jpa.save(mapper.toEntity(period)));
    }

    @Override
    public List<AccountingPeriod> findAll() {
        return jpa.findAllByOrderByPeriodDesc().stream().map(mapper::toDomain).toList();
    }
}
