package co.posinvent.infrastructure.adapters.out.persistence;

import co.posinvent.domain.model.WasteDisposal;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface WasteDisposalMapper {
    @Mapping(target = "dispositionType", expression = "java(co.posinvent.domain.model.DisposalType.valueOf(e.getDispositionType()))")
    WasteDisposal toDomain(WasteDisposalEntity e);
    @Mapping(target = "dispositionType", expression = "java(d.dispositionType().name())")
    @Mapping(target = "version", ignore = true)
    WasteDisposalEntity toEntity(WasteDisposal d);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "dispositionType", expression = "java(d.dispositionType().name())")
    @Mapping(target = "version", ignore = true)
    void updateEntity(@MappingTarget WasteDisposalEntity entity, WasteDisposal d);
}
