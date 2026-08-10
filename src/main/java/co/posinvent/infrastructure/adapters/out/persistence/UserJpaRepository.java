package co.posinvent.infrastructure.adapters.out.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface UserJpaRepository extends JpaRepository<UserEntity, UUID> {
    Optional<UserEntity> findByUsernameAndActiveTrue(String username);
    Optional<UserEntity> findByUsername(String username);
    Optional<UserEntity> findByEmail(String email);

    @Query(value = """
        SELECT u.* FROM users u JOIN roles r ON r.id = u.role_id
        WHERE (CAST(:searchPattern AS text) IS NULL OR u.username ILIKE CAST(:searchPattern AS text)
               OR u.full_name ILIKE CAST(:searchPattern AS text)
               OR u.email ILIKE CAST(:searchPattern AS text))
          AND (CAST(:roleName AS text) IS NULL OR r.name = CAST(:roleName AS text))
          AND (:active IS NULL OR u.is_active = :active)
        ORDER BY u.full_name
        """,
        countQuery = """
        SELECT COUNT(*) FROM users u JOIN roles r ON r.id = u.role_id
        WHERE (CAST(:searchPattern AS text) IS NULL OR u.username ILIKE CAST(:searchPattern AS text)
               OR u.full_name ILIKE CAST(:searchPattern AS text)
               OR u.email ILIKE CAST(:searchPattern AS text))
          AND (CAST(:roleName AS text) IS NULL OR r.name = CAST(:roleName AS text))
          AND (:active IS NULL OR u.is_active = :active)
        """,
        nativeQuery = true)
    Page<UserEntity> findFiltered(
        @Param("searchPattern") String searchPattern,
        @Param("roleName") String roleName,
        @Param("active") Boolean active,
        Pageable pageable
    );

    long countByRoleNameAndActive(String roleName, boolean active);

    boolean existsByEmployeeId(UUID employeeId);

    boolean existsByEmployeeIdAndIdNot(UUID employeeId, UUID id);
}
