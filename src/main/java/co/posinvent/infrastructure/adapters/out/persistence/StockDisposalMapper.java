package co.posinvent.infrastructure.adapters.out.persistence;

import co.posinvent.domain.model.StockDisposal;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface StockDisposalMapper {
    @Mapping(target = "disposalType", expression = "java(co.posinvent.domain.model.DisposalType.valueOf(e.getDisposalType()))")
    StockDisposal toDomain(StockDisposalEntity e);
    @Mapping(target = "disposalType", expression = "java(d.disposalType().name())")
    @Mapping(target = "version", ignore = true)
    StockDisposalEntity toEntity(StockDisposal d);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "disposalType", expression = "java(d.disposalType().name())")
    @Mapping(target = "version", ignore = true)
    void updateEntity(@MappingTarget StockDisposalEntity entity, StockDisposal d);
}
