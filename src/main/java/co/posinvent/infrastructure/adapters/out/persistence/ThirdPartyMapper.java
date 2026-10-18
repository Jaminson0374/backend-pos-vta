package co.posinvent.infrastructure.adapters.out.persistence;

import co.posinvent.domain.model.EmployeeBasicData;
import co.posinvent.domain.model.ThirdParty;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
interface ThirdPartyMapper {

    @Mapping(target = "thirdPartyCategoryId", source = "tpCategoryId")
    @Mapping(target = "isGranContribuyente",  source = "granContribuyente")
    @Mapping(target = "isAutoretenedor",       source = "autoretenedor")
    @Mapping(target = "isAgenteRetencionIva",  source = "agenteRetencionIva")
    @Mapping(target = "isRegimenSimple",       source = "regimenSimple")
    @Mapping(target = "employeeData", expression = "java(mapEmployeeDetails(entity.getEmployeeDetails()))")
    ThirdParty toDomain(ThirdPartyEntity entity);

    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "tpCategoryId", source = "thirdPartyCategoryId")
    @Mapping(target = "granContribuyente",  source = "isGranContribuyente")
    @Mapping(target = "autoretenedor",       source = "isAutoretenedor")
    @Mapping(target = "agenteRetencionIva",  source = "isAgenteRetencionIva")
    @Mapping(target = "regimenSimple",       source = "isRegimenSimple")
    @Mapping(target = "employeeDetails", ignore = true)
    ThirdPartyEntity toEntity(ThirdParty domain);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "tpCategoryId", source = "thirdPartyCategoryId")
    @Mapping(target = "granContribuyente",  source = "isGranContribuyente")
    @Mapping(target = "autoretenedor",       source = "isAutoretenedor")
    @Mapping(target = "agenteRetencionIva",  source = "isAgenteRetencionIva")
    @Mapping(target = "regimenSimple",       source = "isRegimenSimple")
    @Mapping(target = "employeeDetails", ignore = true)
    void updateEntity(@MappingTarget ThirdPartyEntity entity, ThirdParty domain);

    default EmployeeBasicData mapEmployeeDetails(EmployeeDetailsEntity entity) {
        if (entity == null) return null;
        return new EmployeeBasicData(
                entity.getId(),
                entity.getPosition(),
                entity.getCostCenter(),
                entity.getWorkCenter(),
                entity.getGender(),
                entity.getCivilStatus(),
                entity.getSalesGroup(),
                entity.getBirthDate(),
                entity.getBirthPlace(),
                entity.getMilitaryId(),
                entity.isForeigner(),
                entity.isNatResidentExterior(),
                entity.isDeclarant(),
                entity.getAssociatedSeller(),
                entity.isRequiresEndowment(),
                entity.isSenior()
        );
    }
}
