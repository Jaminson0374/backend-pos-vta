package co.posinvent.infrastructure.adapters.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

interface DiscountAuthorizationJpaRepository extends JpaRepository<DiscountAuthorizationEntity, UUID> {

    /**
     * Atomic single-use flip. Updates only when the token is still unused so two
     * concurrent checkouts cannot both consume the same authorization.
     */
    @Modifying
    @Query("update DiscountAuthorizationEntity a set a.used = true where a.id = :id and a.used = false")
    int markUsedIfUnused(@Param("id") UUID id);
}
