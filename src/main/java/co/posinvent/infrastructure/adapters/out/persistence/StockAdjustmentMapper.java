package co.posinvent.infrastructure.adapters.out.persistence;

import co.posinvent.domain.model.StockAdjustment;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface StockAdjustmentMapper {

    @Mapping(target = "adjustmentType", expression = "java(co.posinvent.domain.model.AdjustmentType.valueOf(e.getAdjustmentType()))")
    StockAdjustment toDomain(StockAdjustmentEntity e);

    @Mapping(target = "adjustmentType", expression = "java(a.adjustmentType().name())")
    @Mapping(target = "version", ignore = true)
    StockAdjustmentEntity toEntity(StockAdjustment a);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "adjustmentType", expression = "java(a.adjustmentType().name())")
    @Mapping(target = "version", ignore = true)
    void updateEntity(@MappingTarget StockAdjustmentEntity entity, StockAdjustment a);
}
