package co.posinvent.integration;

import co.posinvent.application.service.JwtBlacklistService;
import co.posinvent.application.usecase.LogoutUseCase;
import co.posinvent.infrastructure.adapters.out.persistence.JwtBlacklistJpaRepository;
import co.posinvent.infrastructure.adapters.out.persistence.RoleEntity;
import co.posinvent.infrastructure.adapters.out.persistence.RoleJpaRepository;
import co.posinvent.infrastructure.adapters.out.persistence.UserEntity;
import co.posinvent.infrastructure.adapters.out.persistence.UserJpaRepository;
import co.posinvent.infrastructure.config.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end proof of per-token revocation (S2b).
 *
 * <p>Logging out must blacklist only the presented token's {@code jti}; a second
 * token issued to the same user stays usable.</p>
 */
class LogoutRevocationIntegrationTest extends AbstractIntegrationTest {

    @Autowired private LogoutUseCase logoutUseCase;
    @Autowired private JwtBlacklistService blacklistService;
    @Autowired private JwtService jwtService;
    @Autowired private JwtBlacklistJpaRepository blacklistRepository;
    @Autowired private UserJpaRepository userJpaRepository;
    @Autowired private RoleJpaRepository roleJpaRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @Test
    void logoutRevokesOnlyThePresentedToken() {
        var suffix = UUID.randomUUID().toString().substring(0, 8);
        var username = "logout-revocation-" + suffix;
        var userIdHolder = new UUID[1];
        var roleNameHolder = new String[1];
        var tokenVersionHolder = new long[1];

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            var user = new UserEntity();
            user.setUsername(username);
            user.setPasswordHash(passwordEncoder.encode("Secret123!"));
            user.setEmail(username + "@example.com");
            user.setFullName("Logout Revocation User");
            user.setRole(roleByName("CONTADOR"));
            user.setActive(true);
            user.setCreatedAt(OffsetDateTime.now());
            user.setUpdatedAt(OffsetDateTime.now());
            userJpaRepository.save(user);

            userIdHolder[0] = user.getId();
            roleNameHolder[0] = user.getRole().getName();
            tokenVersionHolder[0] = user.getTokenVersion();
        });

        var firstToken = jwtService.generateToken(
                userIdHolder[0], username, roleNameHolder[0], null, tokenVersionHolder[0]);
        var firstJti = UUID.fromString(jwtService.extractAllClaims(firstToken).getId());

        logoutUseCase.logout(firstToken);

        var revoked = blacklistRepository.findById(firstJti).orElseThrow();
        assertThat(revoked.getExpiresAt()).isAfter(OffsetDateTime.now());
        assertThat(revoked.getRevokedAt()).isNotNull();
        assertThat(blacklistService.isRevoked(firstJti)).isTrue();

        var secondToken = jwtService.generateToken(
                userIdHolder[0], username, roleNameHolder[0], null, tokenVersionHolder[0]);
        var secondJti = UUID.fromString(jwtService.extractAllClaims(secondToken).getId());

        assertThat(secondJti).isNotEqualTo(firstJti);
        assertThat(blacklistService.isRevoked(secondJti)).isFalse();
        assertThat(blacklistRepository.existsById(secondJti)).isFalse();
    }

    private RoleEntity roleByName(String name) {
        return roleJpaRepository.findAll().stream()
                .filter(role -> name.equals(role.getName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing seeded role " + name));
    }
}
