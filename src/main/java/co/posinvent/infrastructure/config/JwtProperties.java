package co.posinvent.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(
        @DefaultValue("posinvent-dev-jwt-secret-key-v1-must-be-at-least-64-bytes-for-hs512!!")
        String secret,
        @DefaultValue("86400000")
        long expirationMs
) {}
