package co.posinvent.infrastructure.adapters.out.persistence;

import co.posinvent.domain.model.Tax;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
interface TaxMapper {

    Tax toDomain(TaxEntity entity);

    @Mapping(target = "id", ignore = true)
    TaxEntity toEntity(Tax domain);
}
