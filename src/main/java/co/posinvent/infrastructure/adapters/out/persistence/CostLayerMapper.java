package co.posinvent.infrastructure.adapters.out.persistence;

import co.posinvent.domain.model.CostLayer;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface CostLayerMapper {

    CostLayer toDomain(CostLayerEntity e);

    @Mapping(target = "version", ignore = true)
    CostLayerEntity toEntity(CostLayer layer);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "version", ignore = true)
    void updateEntity(@MappingTarget CostLayerEntity entity, CostLayer layer);
}
