package co.posinvent.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuration for the Siigo DIAN provider adapter ({@code app.dian.siigo}).
 *
 * <p>Credentials ({@code username}, {@code accessKey}, {@code partnerId}) have no inline
 * defaults on purpose: they must come from environment variables. Only non-secret routing
 * values fall back to safe defaults so the adapter can boot without a Siigo account.</p>
 */
@ConfigurationProperties(prefix = "app.dian.siigo")
public record DianSiigoProperties(
        String username,
        String accessKey,
        String partnerId,
        @DefaultValue("https://api.siigo.com")
        String baseUrl,
        @DefaultValue("/auth")
        String authPath,
        @DefaultValue("/v1/invoices")
        String invoicePath,
        @DefaultValue("/v1/invoices/status")
        String statusPath,
        @DefaultValue("5000")
        int connectTimeoutMs,
        @DefaultValue("10000")
        int readTimeoutMs
) {}
