package co.posinvent.infrastructure.adapters.out.persistence;

import co.posinvent.domain.model.ThirdParty.ThirdPartyType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

interface ThirdPartyJpaRepository extends JpaRepository<ThirdPartyEntity, UUID> {

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
}
