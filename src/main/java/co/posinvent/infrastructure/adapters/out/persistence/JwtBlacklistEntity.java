package co.posinvent.infrastructure.adapters.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "jwt_blacklist")
@Getter
@Setter
public class JwtBlacklistEntity {

    @Id
    private UUID jti;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "revoked_at", nullable = false)
    private OffsetDateTime revokedAt;
}
