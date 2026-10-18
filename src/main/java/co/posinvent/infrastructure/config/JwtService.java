package co.posinvent.infrastructure.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

@Service
public class JwtService {

    private final SecretKey key;
    private final long expirationMs;

    private static final String LEGACY_DEV_SECRET_PREFIX = "posinvent-dev-jwt-secret-key";

    public JwtService(JwtProperties props) {
        String secret = props.secret();
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("JWT_SECRET is required (app.jwt.secret)");
        }
        if (secret.startsWith(LEGACY_DEV_SECRET_PREFIX)) {
            throw new IllegalStateException("JWT_SECRET must not use the legacy development default");
        }
        byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < 64) {
            throw new IllegalStateException("JWT_SECRET must be at least 64 bytes for HS512");
        }
        this.key = Keys.hmacShaKeyFor(secretBytes);
        this.expirationMs = props.expirationMs();
    }

    public String generateToken(UUID userId, String username, String role, UUID employeeId) {
        var builder = Jwts.builder()
                .subject(username)
                .claim("uid", userId.toString())
                .claim("role", role)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expirationMs));

        if (employeeId != null) {
            builder.claim("eid", employeeId.toString());
        }

        return builder.signWith(key, Jwts.SIG.HS512).compact();
    }

    public Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String extractUsername(String token) {
        return extractAllClaims(token).getSubject();
    }

    public boolean isTokenValid(String token) {
        try {
            return extractAllClaims(token).getExpiration().after(new Date());
        } catch (Exception e) {
            return false;
        }
    }
}
