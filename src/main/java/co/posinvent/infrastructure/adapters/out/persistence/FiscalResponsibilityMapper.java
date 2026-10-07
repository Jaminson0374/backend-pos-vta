package co.posinvent.infrastructure.adapters.out.persistence;

import co.posinvent.domain.model.FiscalResponsibility;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
interface FiscalResponsibilityMapper {

    FiscalResponsibility toDomain(FiscalResponsibilityEntity entity);
}
