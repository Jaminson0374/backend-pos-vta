package co.posinvent.infrastructure.adapters.out.persistence;

import co.posinvent.domain.model.InventoryStock;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
interface StockMapper {

    InventoryStock toDomain(InventoryStockEntity entity);

    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "version", ignore = true)
    InventoryStockEntity toEntity(InventoryStock domain);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "version", ignore = true)
    void updateEntity(@MappingTarget InventoryStockEntity entity, InventoryStock domain);
}
