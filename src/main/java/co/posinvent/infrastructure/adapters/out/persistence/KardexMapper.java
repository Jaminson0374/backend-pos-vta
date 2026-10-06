package co.posinvent.infrastructure.adapters.out.persistence;

import co.posinvent.domain.model.InventoryMovement;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface KardexMapper {

    @Mapping(target = "movementType", expression = "java(co.posinvent.domain.model.MovementType.valueOf(e.getMovementType()))")
    InventoryMovement toDomain(InventoryMovementEntity e);

    @Mapping(target = "movementType", expression = "java(m.movementType().name())")
    @Mapping(target = "version", ignore = true)
    InventoryMovementEntity toEntity(InventoryMovement m);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "movementType", expression = "java(m.movementType().name())")
    @Mapping(target = "version", ignore = true)
    void updateEntity(@MappingTarget InventoryMovementEntity entity, InventoryMovement m);
}
