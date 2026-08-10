package co.posinvent.infrastructure.adapters.out.persistence;

import co.posinvent.domain.model.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", uses = RoleMapper.class)
public interface UserMapper {

    @Mapping(source = "active", target = "isActive")
    @Mapping(target = "employeeId", expression = "java(entity.getEmployee() != null ? entity.getEmployee().getId() : null)")
    @Mapping(target = "employeeName", expression = "java(entity.getEmployee() != null ? entity.getEmployee().getName() : null)")
    User toDomain(UserEntity entity);

    @Mapping(source = "isActive", target = "active")
    @Mapping(target = "passwordHash", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "employee", ignore = true)
    UserEntity toEntity(User domain);
}
