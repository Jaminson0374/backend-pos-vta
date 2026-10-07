package co.posinvent.infrastructure.adapters.out.persistence;

import co.posinvent.domain.model.TaxResponsibility;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
interface TaxResponsibilityMapper {

    TaxResponsibility toDomain(TaxResponsibilityEntity entity);
}
