package co.posinvent.infrastructure.adapters.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface CashCountJpaRepository extends JpaRepository<CashCountEntity, UUID> {

    List<CashCountEntity> findByShiftIdOrderByCreatedAtDesc(UUID shiftId);
}
