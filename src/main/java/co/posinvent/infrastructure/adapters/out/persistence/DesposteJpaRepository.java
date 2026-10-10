package co.posinvent.infrastructure.adapters.out.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.UUID;

interface DesposteJpaRepository extends JpaRepository<DesposteEntity, UUID> {

    Page<DesposteEntity> findByCreatedAtGreaterThanEqual(OffsetDateTime from, Pageable pageable);

    Page<DesposteEntity> findByCreatedAtLessThan(OffsetDateTime toExclusive, Pageable pageable);

    Page<DesposteEntity> findByCreatedAtGreaterThanEqualAndCreatedAtLessThan(
            OffsetDateTime from, OffsetDateTime toExclusive, Pageable pageable);
}
