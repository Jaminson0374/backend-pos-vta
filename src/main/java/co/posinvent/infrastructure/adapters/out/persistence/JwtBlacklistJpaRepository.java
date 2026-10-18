package co.posinvent.infrastructure.adapters.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.UUID;

public interface JwtBlacklistJpaRepository extends JpaRepository<JwtBlacklistEntity, UUID> {

    @Modifying
    @Query("delete from JwtBlacklistEntity b where b.expiresAt < :cutoff")
    int deleteExpiredBefore(@Param("cutoff") OffsetDateTime cutoff);

    @Modifying
    @Query(value = "INSERT INTO jwt_blacklist (jti, expires_at, revoked_at) VALUES (:jti, :expiresAt, NOW()) "
            + "ON CONFLICT (jti) DO UPDATE SET expires_at = EXCLUDED.expires_at",
            nativeQuery = true)
    void upsert(@Param("jti") UUID jti, @Param("expiresAt") OffsetDateTime expiresAt);
}
