package co.posinvent.infrastructure.audit;

import co.posinvent.domain.repository.CurrentUserProvider;
import org.springframework.data.domain.AuditorAware;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Resolves the current authenticated user's ID for Spring Data JPA auditing.
 * Used by {@code @CreatedBy} and {@code @LastModifiedBy} annotations.
 * Delegates to {@link CurrentUserProvider} so the security-context lookup
 * has a single source of truth.
 */
@Component
public class AuditorAwareImpl implements AuditorAware<UUID> {

    private final CurrentUserProvider currentUserProvider;

    public AuditorAwareImpl(CurrentUserProvider currentUserProvider) {
        this.currentUserProvider = currentUserProvider;
    }

    @Override
    public Optional<UUID> getCurrentAuditor() {
        return currentUserProvider.currentUserId();
    }
}
