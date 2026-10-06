package co.posinvent.infrastructure.adapters.out.persistence;

import co.posinvent.domain.model.StockTransfer;
import co.posinvent.domain.model.StockTransferItem;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.util.Collections;
import java.util.List;

@Mapper(componentModel = "spring")
public interface StockTransferMapper {

    @Mapping(target = "status", expression = "java(co.posinvent.domain.model.TransferStatus.valueOf(e.getStatus()))")
    @Mapping(target = "items", expression = "java(mapItems(e.getItems()))")
    StockTransfer toDomain(StockTransferEntity e);

    @Mapping(target = "status", expression = "java(t.status().name())")
    @Mapping(target = "items", ignore = true)
    @Mapping(target = "version", ignore = true)
    StockTransferEntity toEntity(StockTransfer t);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "status", expression = "java(t.status().name())")
    @Mapping(target = "items", ignore = true)
    @Mapping(target = "version", ignore = true)
    void updateEntity(@MappingTarget StockTransferEntity entity, StockTransfer t);

    @Mapping(target = "transfer", ignore = true)
    StockTransferItemEntity toItemEntity(StockTransferItem item);

    default List<StockTransferItem> mapItems(List<StockTransferItemEntity> entities) {
        if (entities == null) return Collections.emptyList();
        return entities.stream().map(this::toItemDomain).toList();
    }

    @Mapping(target = "transferId", expression = "java(e.getTransfer().getId())")
    StockTransferItem toItemDomain(StockTransferItemEntity e);
}
