package co.posinvent.infrastructure.adapters.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.UUID;

public interface JwtBlacklistJpaRepository extends JpaRepository<JwtBlacklistEntity, UUID> {

    long deleteByExpiresAtBefore(OffsetDateTime cutoff);
}
