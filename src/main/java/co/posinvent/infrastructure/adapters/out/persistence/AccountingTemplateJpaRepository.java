package co.posinvent.infrastructure.adapters.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

interface AccountingTemplateJpaRepository extends JpaRepository<AccountingTemplateEntity, UUID> {
    Optional<AccountingTemplateEntity> findByCode(String code);
    Optional<AccountingTemplateEntity> findByModuleAndIsDefaultTrue(String module);
    java.util.List<AccountingTemplateEntity> findByModule(String module);
}
