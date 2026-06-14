package co.posinvent.infrastructure.adapters.out.persistence;

import co.posinvent.domain.model.AccountingTemplate;
import co.posinvent.domain.model.AccountingTemplateEntry;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
interface AccountingTemplateMapper {

    @Mapping(source = "default", target = "isDefault")
    @Mapping(source = "active", target = "isActive")
    AccountingTemplate toDomain(AccountingTemplateEntity entity);

    @Mapping(source = "template.id", target = "templateId")
    @Mapping(source = "debit", target = "isDebit")
    AccountingTemplateEntry toDomainEntry(AccountingTemplateEntryEntity entity);

    @Mapping(source = "isDefault", target = "default")
    @Mapping(source = "isActive", target = "active")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    AccountingTemplateEntity toEntity(AccountingTemplate domain);

    @Mapping(source = "isDebit", target = "debit")
    @Mapping(target = "template", ignore = true)
    AccountingTemplateEntryEntity toEntityEntry(AccountingTemplateEntry domain);
}
