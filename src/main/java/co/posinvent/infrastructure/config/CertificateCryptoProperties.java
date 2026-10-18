package co.posinvent.infrastructure.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.security.certificate")
public record CertificateCryptoProperties(
        @NotBlank
        String secretKey
) {}
