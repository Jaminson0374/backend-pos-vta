package co.posinvent.application.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Issued single-use discount authorization token. The checkout carries only
 * {@code authorizationId}; it never sees the administrator credentials.
 */
public record DiscountAuthorizationResponse(
        UUID authorizationId,
        OffsetDateTime expiresAt,
        String authorizedBy
) {
}
