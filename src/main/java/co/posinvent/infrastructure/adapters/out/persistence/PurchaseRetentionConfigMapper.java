package co.posinvent.infrastructure.adapters.out.persistence;

import co.posinvent.domain.model.PurchaseRetentionConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
interface PurchaseRetentionConfigMapper {

    PurchaseRetentionConfig toDomain(PurchaseRetentionConfigEntity entity);

    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    PurchaseRetentionConfigEntity toEntity(PurchaseRetentionConfig domain);
}
