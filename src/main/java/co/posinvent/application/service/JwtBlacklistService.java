package co.posinvent.application.service;

import co.posinvent.infrastructure.adapters.out.persistence.JwtBlacklistJpaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * Stores revoked JWT identifiers (jti) until their natural expiration.
 *
 * <p>The auth filter consults {@link #isRevoked(UUID)} on every request, so a
 * logout takes effect immediately instead of waiting for the token to expire.</p>
 */
@Service
public class JwtBlacklistService {

    private static final Logger log = LoggerFactory.getLogger(JwtBlacklistService.class);

    private final JwtBlacklistJpaRepository repository;

    public JwtBlacklistService(JwtBlacklistJpaRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void revoke(UUID jti, OffsetDateTime expiresAt) {
        repository.upsert(jti, expiresAt);
    }

    @Transactional(readOnly = true)
    public boolean isRevoked(UUID jti) {
        return repository.existsById(jti);
    }

    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void purgeExpired() {
        int purged = repository.deleteExpiredBefore(OffsetDateTime.now(ZoneOffset.UTC));
        log.info("purged {} expired JWT blacklist rows", purged);
    }
}
