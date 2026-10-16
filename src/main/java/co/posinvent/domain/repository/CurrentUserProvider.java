package co.posinvent.domain.repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Outbound port that resolves the currently authenticated user's ID.
 * Returns {@link Optional#empty()} when there is no authenticated user
 * (e.g. background jobs), letting callers apply a fallback.
 */
public interface CurrentUserProvider {

    Optional<UUID> currentUserId();
}
