package co.posinvent.domain.model;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Single-use authorization token for a discount that exceeds policy (M2 / WU-27).
 *
 * <p>An administrator approves the discount through the POS; the resulting token
 * carries only the approving admin ({@code authorizedBy}) and a short expiry. The
 * checkout consumes it exactly once, recording {@code authorizedBy} on the invoice.</p>
 */
public record DiscountAuthorization(
        UUID id,
        UUID requestedBy,
        UUID authorizedBy,
        UUID cashRegisterId,
        OffsetDateTime expiresAt,
        boolean used,
        OffsetDateTime createdAt
) {
}
