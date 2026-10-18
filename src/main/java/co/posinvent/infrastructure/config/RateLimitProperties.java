package co.posinvent.infrastructure.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.security.rate-limit")
public record RateLimitProperties(
        @DefaultValue("false")
        boolean trustForwardedHeaders,
        @DefaultValue("5") @Min(1)
        int loginCapacity,
        @DefaultValue("60") @Min(1)
        int loginWindowSeconds,
        @DefaultValue("3") @Min(1)
        int forgotPasswordCapacity,
        @DefaultValue("60") @Min(1)
        int forgotPasswordWindowSeconds,
        @DefaultValue("5") @Min(1)
        int setPasswordCapacity,
        @DefaultValue("60") @Min(1)
        int setPasswordWindowSeconds,
        @DefaultValue("10000") @Min(1)
        int maxBuckets
) {}
