package co.posinvent.infrastructure.adapters.out.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DianResolutionJpaRepository extends JpaRepository<DianResolutionEntity, UUID> {

    Optional<DianResolutionEntity> findFirstByActiveTrueOrderByCreatedAtDesc();

    /**
     * Locks the active resolution row for the current transaction so concurrent
     * checkouts serialize on the consecutive number instead of racing.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from DianResolutionEntity r where r.active = true order by r.createdAt desc")
    List<DianResolutionEntity> lockActive();

    @Modifying
    @Query("UPDATE DianResolutionEntity r SET r.active = false WHERE r.active = true AND r.id != :id")
    void deactivateAllExcept(@Param("id") UUID id);

    @Modifying
    @Query("UPDATE DianResolutionEntity r SET r.active = false")
    void deactivateAll();
}
