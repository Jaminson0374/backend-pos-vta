package co.posinvent.infrastructure.adapters.out.persistence;

import co.posinvent.domain.model.AccountingTemplate;
import co.posinvent.domain.repository.AccountingTemplateRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
class AccountingTemplateRepositoryAdapter implements AccountingTemplateRepository {

    private final AccountingTemplateJpaRepository jpa;
    private final AccountingTemplateMapper mapper;

    AccountingTemplateRepositoryAdapter(AccountingTemplateJpaRepository jpa, AccountingTemplateMapper mapper) {
        this.jpa = jpa;
        this.mapper = mapper;
    }

    @Override
    public List<AccountingTemplate> findAll() {
        return jpa.findAll().stream().map(mapper::toDomain).toList();
    }

    @Override
    public Optional<AccountingTemplate> findById(UUID id) {
        return jpa.findById(id).map(mapper::toDomain);
    }

    @Override
    public Optional<AccountingTemplate> findByCode(String code) {
        return jpa.findByCode(code).map(mapper::toDomain);
    }

    @Override
    public Optional<AccountingTemplate> findDefaultByModule(String module) {
        return jpa.findByModuleAndIsDefaultTrue(module).map(mapper::toDomain);
    }

    @Override
    public AccountingTemplate save(AccountingTemplate domain) {
        var entity = mapper.toEntity(domain);
        var saved = jpa.save(entity);
        return jpa.findById(saved.getId()).map(mapper::toDomain).orElseThrow();
    }

    @Override
    public void deleteById(UUID id) {
        jpa.deleteById(id);
    }

    @Override
    public boolean existsByCode(String code) {
        return jpa.findByCode(code).isPresent();
    }
}
