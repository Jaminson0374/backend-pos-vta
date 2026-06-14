package co.posinvent.infrastructure.adapters.out.persistence;

import co.posinvent.domain.model.AccountsPayable;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface AccountsPayableMapper {

    @Mapping(target = "status", expression = "java(co.posinvent.domain.model.AccountsPayable.ApStatus.valueOf(e.getStatus()))")
    AccountsPayable toDomain(AccountsPayableEntity e);

    @Mapping(target = "status", expression = "java(a.status().name())")
    AccountsPayableEntity toEntity(AccountsPayable a);
}
