package co.posinvent.infrastructure.audit;

import co.posinvent.infrastructure.adapters.out.security.PosUserDetails;
import org.springframework.data.domain.AuditorAware;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Resolves the current authenticated user's ID for Spring Data JPA auditing.
 * Used by {@code @CreatedBy} and {@code @LastModifiedBy} annotations.
 */
@Component
public class AuditorAwareImpl implements AuditorAware<UUID> {

    @Override
    public Optional<UUID> getCurrentAuditor() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return Optional.empty();
        }

        Object principal = auth.getPrincipal();
        if (principal instanceof PosUserDetails user) {
            return Optional.of(user.userId());
        }

        return Optional.empty();
    }
}
