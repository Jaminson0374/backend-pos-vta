package co.posinvent.domain.repository;

import co.posinvent.domain.model.DiscountAuthorization;

import java.util.Optional;
import java.util.UUID;

/**
 * Outbound port for the single-use discount authorization tokens (M2 / WU-27).
 */
public interface DiscountAuthorizationRepository {

    DiscountAuthorization save(DiscountAuthorization authorization);

    Optional<DiscountAuthorization> findById(UUID id);

    /**
     * Atomically marks the token as consumed and reports whether this call won the flip.
     * Single use: only the first caller observes {@code true}, so two concurrent checkouts
     * cannot both consume the same authorization.
     *
     * @return {@code true} when this call flipped the token from unused to used,
     *         {@code false} when it was already used (or no longer exists).
     */
    boolean markUsedIfUnused(UUID id);
}
