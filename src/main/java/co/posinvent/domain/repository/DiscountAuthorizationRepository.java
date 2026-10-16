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
     * Marks the token as consumed. Single use: after this call the token can no
     * longer authorize another checkout.
     */
    void markUsed(UUID id);
}
