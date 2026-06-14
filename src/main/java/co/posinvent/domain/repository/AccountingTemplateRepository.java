package co.posinvent.domain.repository;

import co.posinvent.domain.model.AccountingTemplate;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccountingTemplateRepository {
    List<AccountingTemplate> findAll();
    Optional<AccountingTemplate> findById(UUID id);
    Optional<AccountingTemplate> findByCode(String code);
    Optional<AccountingTemplate> findDefaultByModule(String module);
    AccountingTemplate save(AccountingTemplate template);
    void deleteById(UUID id);
    boolean existsByCode(String code);
}
