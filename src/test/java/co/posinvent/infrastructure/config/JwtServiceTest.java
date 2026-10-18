package co.posinvent.infrastructure.config;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final long EXPIRATION_MS = 86_400_000L;
    private static final String LEGACY_DEV_DEFAULT =
            "posinvent-dev-jwt-secret-key-v1-must-be-at-least-64-bytes-for-hs512!!";

    private static JwtService serviceWithSecret(String secret) {
        return new JwtService(new JwtProperties(secret, EXPIRATION_MS));
    }

    @Test
    void blankSecretFailsFast() {
        assertThatThrownBy(() -> serviceWithSecret("   "))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET is required");
    }

    @Test
    void shortSecretFailsFast() {
        assertThatThrownBy(() -> serviceWithSecret("a".repeat(32)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 64 bytes");
    }

    @Test
    void legacyDevelopmentDefaultFailsFast() {
        assertThatThrownBy(() -> serviceWithSecret(LEGACY_DEV_DEFAULT))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("legacy development default");
    }

    @Test
    void validSecretGeneratesAndRoundTripsToken() {
        var service = serviceWithSecret("a-valid-random-jwt-secret-with-more-than-sixty-four-characters-1234");
        var userId = UUID.randomUUID();
        var username = "admin@posinvent.co";
        var role = "ADMIN";

        var token = service.generateToken(userId, username, role, null, 7L);

        assertThat(token).isNotBlank();
        assertThat(service.extractUsername(token)).isEqualTo(username);
        assertThat(service.isTokenValid(token)).isTrue();
        assertThat(((Number) service.extractAllClaims(token).get("token_version")).longValue())
                .isEqualTo(7L);
        assertThat(service.extractAllClaims(token).getId()).isNotBlank();
    }

    @Test
    void eachTokenGetsADistinctJti() {
        var service = serviceWithSecret("a-valid-random-jwt-secret-with-more-than-sixty-four-characters-1234");
        var userId = UUID.randomUUID();

        var first = service.generateToken(userId, "admin@posinvent.co", "ADMIN", null, 0L);
        var second = service.generateToken(userId, "admin@posinvent.co", "ADMIN", null, 0L);

        assertThat(service.extractAllClaims(first).getId())
                .isNotBlank()
                .isNotEqualTo(service.extractAllClaims(second).getId());
    }
}
