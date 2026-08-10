package co.posinvent.infrastructure.adapters.out.persistence;

import co.posinvent.domain.model.PucAccount;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
interface PucAccountMapper {
    PucAccount toDomain(PucAccountEntity entity);

    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "level", expression = "java((short) domain.level())")
    @Mapping(target = "accountClass", expression = "java((short) domain.accountClass())")
    PucAccountEntity toEntity(PucAccount domain);
}
