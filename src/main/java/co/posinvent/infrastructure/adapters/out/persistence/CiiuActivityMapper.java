package co.posinvent.infrastructure.adapters.out.persistence;

import co.posinvent.domain.model.CiiuActivity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
interface CiiuActivityMapper {

    CiiuActivity toDomain(CiiuActivityEntity entity);
}
