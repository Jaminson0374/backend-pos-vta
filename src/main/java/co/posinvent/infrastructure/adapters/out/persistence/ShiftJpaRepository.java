package co.posinvent.infrastructure.adapters.out.persistence;

import co.posinvent.domain.model.ShiftStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

interface ShiftJpaRepository extends JpaRepository<ShiftEntity, UUID> {

    Optional<ShiftEntity> findByCashRegisterIdAndStatus(UUID cashRegisterId, ShiftStatus status);

    Page<ShiftEntity> findByUserIdOrAssignedTo(UUID userId, UUID assignedTo, Pageable pageable);
}
