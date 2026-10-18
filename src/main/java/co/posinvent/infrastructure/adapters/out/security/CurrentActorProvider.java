package co.posinvent.infrastructure.adapters.out.security;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Resolves the authenticated actor (user id + role) for application-layer ownership checks.
 */
@Component
public class CurrentActorProvider {

    private static final String ROLE_PREFIX = "ROLE_";

    public CurrentActor current() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof PosUserDetails user)) {
            throw new AccessDeniedException("No hay un usuario autenticado para realizar esta acción.");
        }

        String role = user.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(authority -> authority != null && authority.startsWith(ROLE_PREFIX))
                .map(authority -> authority.substring(ROLE_PREFIX.length()))
                .findFirst()
                .orElse(null);

        return new CurrentActor(user.userId(), role);
    }

    public record CurrentActor(UUID userId, String role) {

        public boolean isAdmin() {
            return "ADMIN".equals(role);
        }

        public boolean isContador() {
            return "CONTADOR".equals(role);
        }
    }
}
