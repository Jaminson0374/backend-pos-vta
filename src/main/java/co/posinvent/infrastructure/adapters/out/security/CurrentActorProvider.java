package co.posinvent.infrastructure.adapters.out.security;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/**
 * Resolves the authenticated actor (user id + full role set) for application-layer
 * ownership checks.
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

        Set<String> roles = new TreeSet<>();
        for (GrantedAuthority authority : user.getAuthorities()) {
            String value = authority == null ? null : authority.getAuthority();
            if (value != null && value.startsWith(ROLE_PREFIX)) {
                roles.add(value.substring(ROLE_PREFIX.length()).toUpperCase(Locale.ROOT));
            }
        }

        return new CurrentActor(user.userId(), roles);
    }

    /**
     * A principal may hold more than one role (e.g. ADMIN + CAJERO). Roles are normalized
     * to uppercase and stored in a sorted, immutable set so behavior never depends on the
     * order authorities arrive in.
     */
    public record CurrentActor(UUID userId, Set<String> roles) {

        public CurrentActor {
            Set<String> copy = new TreeSet<>();
            if (roles != null) {
                copy.addAll(roles);
            }
            roles = Collections.unmodifiableSet(copy);
        }

        public boolean hasRole(String role) {
            return role != null && roles.contains(role.toUpperCase(Locale.ROOT));
        }

        public boolean hasAnyRole(String... candidates) {
            if (candidates == null) {
                return false;
            }
            for (String candidate : candidates) {
                if (hasRole(candidate)) {
                    return true;
                }
            }
            return false;
        }

        public boolean isAdmin() {
            return hasRole("ADMIN");
        }

        public boolean isContador() {
            return hasRole("CONTADOR");
        }

        /**
         * Role used to evaluate the manual-discount ceiling. ADMIN dominates, then CAJERO;
         * any other role falls back to the first sorted role so the result is stable.
         */
        public String primaryRoleForDiscountPolicy() {
            if (isAdmin()) {
                return "ADMIN";
            }
            if (hasRole("CAJERO")) {
                return "CAJERO";
            }
            return roles.stream().findFirst().orElse(null);
        }
    }
}
