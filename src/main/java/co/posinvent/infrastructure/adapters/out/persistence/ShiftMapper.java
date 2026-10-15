package co.posinvent.infrastructure.adapters.out.persistence;

import co.posinvent.domain.model.Shift;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
interface ShiftMapper {

    // Lombok derives the entity getter/setter as getZReportUrl()/setZReportUrl(), so the
    // Java-Bean property is "ZReportUrl" (capital Z) and does not match the record's
    // "zReportUrl". Map both directions explicitly to avoid silently dropping the value.
    @Mapping(target = "zReportUrl", source = "ZReportUrl")
    Shift toDomain(ShiftEntity entity);

    @Mapping(target = "ZReportUrl", source = "zReportUrl")
    ShiftEntity toEntity(Shift domain);
}
