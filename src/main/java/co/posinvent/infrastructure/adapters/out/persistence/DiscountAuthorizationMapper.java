package co.posinvent.infrastructure.adapters.out.persistence;

import co.posinvent.domain.model.DiscountAuthorization;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
interface DiscountAuthorizationMapper {

    DiscountAuthorization toDomain(DiscountAuthorizationEntity entity);

    @Mapping(target = "createdAt", ignore = true)
    DiscountAuthorizationEntity toEntity(DiscountAuthorization domain);
}
