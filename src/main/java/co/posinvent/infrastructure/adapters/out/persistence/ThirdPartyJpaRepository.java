package co.posinvent.infrastructure.adapters.out.persistence;

import co.posinvent.domain.model.ThirdParty.ThirdPartyType;
import co.posinvent.domain.model.ThirdParty.PersonType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ThirdPartyJpaRepository extends JpaRepository<ThirdPartyEntity, UUID> {

    boolean existsByNumIdentification(String numIdentification);

    boolean existsByNumIdentificationAndIdNot(String numIdentification, UUID id);

    @Query("""
             SELECT t FROM ThirdPartyEntity t
             WHERE LOWER(t.name) LIKE :q
                OR t.numIdentification LIKE :q
             """)
    Page<ThirdPartyEntity> search(@Param("q") String query, Pageable pageable);

    @Query("""
            SELECT DISTINCT t FROM ThirdPartyEntity t
            LEFT JOIN ThirdPartyCategoryEntity c ON c.id = t.tpCategoryId
            WHERE t.type IN :types
               OR c.baseType IN :baseTypes
            ORDER BY t.name ASC, t.lastName ASC, t.numIdentification ASC
            """)
    List<ThirdPartyEntity> findSuppliers(
            @Param("types") Collection<ThirdPartyType> types,
            @Param("baseTypes") Collection<String> baseTypes
    );

    Page<ThirdPartyEntity> findByTypeAndActive(ThirdPartyType type, boolean active, Pageable pageable);

    Page<ThirdPartyEntity> findByPersonTypeAndActive(PersonType personType, boolean active, Pageable pageable);

    @Query("""
            SELECT t FROM ThirdPartyEntity t
            WHERE t.type = 'EMPLOYEE'
              AND t.active = true
              AND t.id NOT IN (SELECT u.employee.id FROM UserEntity u WHERE u.employee IS NOT NULL)
            ORDER BY t.name ASC
            """)
    List<ThirdPartyEntity> findEmployeesWithoutUser();

    Optional<ThirdPartyEntity> findByNumIdentification(String numIdentification);
}
