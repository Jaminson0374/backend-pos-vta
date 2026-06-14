package co.posinvent.infrastructure.adapters.out.persistence;

import co.posinvent.domain.model.PurchaseReturn;
import co.posinvent.domain.model.PurchaseReturnStatus;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface PurchaseReturnMapper {

    @Mapping(target = "status", expression = "java(co.posinvent.domain.model.PurchaseReturnStatus.valueOf(e.getStatus()))")
    PurchaseReturn toDomain(PurchaseReturnEntity e);

    @Mapping(target = "status", expression = "java(p.status().name())")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "version", ignore = true)
    PurchaseReturnEntity toEntity(PurchaseReturn p);
}
