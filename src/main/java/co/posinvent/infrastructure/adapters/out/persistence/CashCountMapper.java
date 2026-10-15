package co.posinvent.infrastructure.adapters.out.persistence;

import co.posinvent.domain.model.CashCount;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
interface CashCountMapper {

    CashCount toDomain(CashCountEntity entity);

    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "version", ignore = true)
    CashCountEntity toEntity(CashCount domain);

    CashCount.Line toLineDomain(CashCountLineEntity entity);

    @Mapping(target = "cashCount", ignore = true)
    CashCountLineEntity toLineEntity(CashCount.Line line);
}
