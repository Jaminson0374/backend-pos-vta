package co.posinvent.infrastructure.adapters.out.persistence;

import co.posinvent.domain.model.Tax;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
interface TaxMapper {

    Tax toDomain(TaxEntity entity);
}
