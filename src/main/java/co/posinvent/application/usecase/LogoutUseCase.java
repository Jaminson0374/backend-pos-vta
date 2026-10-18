package co.posinvent.application.usecase;

import co.posinvent.application.service.JwtBlacklistService;
import co.posinvent.domain.exception.BusinessException;
import co.posinvent.infrastructure.config.JwtService;
import io.jsonwebtoken.Claims;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * Revokes the JWT presented on logout by adding its {@code jti} to the blacklist.
 */
@Service
public class LogoutUseCase {

    private final JwtService jwtService;
    private final JwtBlacklistService blacklistService;

    public LogoutUseCase(JwtService jwtService, JwtBlacklistService blacklistService) {
        this.jwtService = jwtService;
        this.blacklistService = blacklistService;
    }

    public void logout(String token) {
        final Claims claims;
        try {
            claims = jwtService.extractAllClaims(token);
        } catch (Exception e) {
            throw new BusinessException("INVALID_TOKEN", "Token inválido");
        }

        var jti = claims.getId();
        if (jti == null || jti.isBlank()) {
            throw new BusinessException("INVALID_TOKEN", "El token no contiene jti");
        }

        final UUID jtiValue;
        try {
            jtiValue = UUID.fromString(jti);
        } catch (IllegalArgumentException e) {
            throw new BusinessException("INVALID_TOKEN", "El token contiene un jti inválido");
        }

        var expiration = claims.getExpiration();
        if (expiration == null) {
            throw new BusinessException("INVALID_TOKEN", "El token no contiene expiración");
        }

        var expiresAt = OffsetDateTime.ofInstant(expiration.toInstant(), ZoneOffset.UTC);
        blacklistService.revoke(jtiValue, expiresAt);
    }
}
