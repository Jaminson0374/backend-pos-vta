package co.posinvent.infrastructure.adapters.out.persistence;

import co.posinvent.domain.model.Desposte;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.util.Collections;
import java.util.List;

@Mapper(componentModel = "spring")
public interface DesposteMapper {

    @Mapping(target = "cuts", expression = "java(mapCuts(e.getCuts()))")
    Desposte toDomain(DesposteEntity e);

    @Mapping(target = "cuts", ignore = true)
    DesposteEntity toEntity(Desposte d);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "cuts", ignore = true)
    void updateEntity(@MappingTarget DesposteEntity entity, Desposte d);

    @Mapping(target = "desposte", ignore = true)
    DesposteCutEntity toCutEntity(Desposte.DesposteCut cut);

    Desposte.DesposteCut toCutDomain(DesposteCutEntity e);

    default List<Desposte.DesposteCut> mapCuts(List<DesposteCutEntity> entities) {
        if (entities == null) {
            return Collections.emptyList();
        }
        return entities.stream().map(this::toCutDomain).toList();
    }
}
