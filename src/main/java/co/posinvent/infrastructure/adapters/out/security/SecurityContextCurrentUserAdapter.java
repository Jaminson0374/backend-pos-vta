package co.posinvent.infrastructure.adapters.out.security;

import co.posinvent.domain.repository.CurrentUserProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Adapter that resolves the current user from the Spring Security context.
 * Single source of truth for reading {@link PosUserDetails} out of the
 * {@link SecurityContextHolder}.
 */
@Component
public class SecurityContextCurrentUserAdapter implements CurrentUserProvider {

    @Override
    public Optional<UUID> currentUserId() {
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
