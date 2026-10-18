package co.posinvent.infrastructure.adapters.out.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CurrentActorProviderTest {

    private static final UUID USER_ID = UUID.randomUUID();

    private final CurrentActorProvider provider = new CurrentActorProvider();

    @BeforeEach
    void setUpContext() {
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void resolvesUserIdAndRoleFromPosUserDetails() {
        authenticate(USER_ID, List.of(new SimpleGrantedAuthority("ROLE_CAJERO")));

        var actor = provider.current();

        assertThat(actor.userId()).isEqualTo(USER_ID);
        assertThat(actor.role()).isEqualTo("CAJERO");
        assertThat(actor.isAdmin()).isFalse();
        assertThat(actor.isContador()).isFalse();
    }

    @Test
    void extractsRoleIgnoringNonRoleAuthorities() {
        authenticate(USER_ID, List.of(
                new SimpleGrantedAuthority("PERM_SOMETHING"),
                new SimpleGrantedAuthority("ROLE_ADMIN")));

        var actor = provider.current();

        assertThat(actor.role()).isEqualTo("ADMIN");
        assertThat(actor.isAdmin()).isTrue();
    }

    @Test
    void detectsContadorRole() {
        authenticate(USER_ID, List.of(new SimpleGrantedAuthority("ROLE_CONTADOR")));

        assertThat(provider.current().isContador()).isTrue();
    }

    @Test
    void throwsWhenThereIsNoAuthentication() {
        assertThatThrownBy(provider::current)
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("autenticado");
    }

    @Test
    void throwsWhenThePrincipalIsNotPosUserDetails() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("unexpected", null, List.of()));

        assertThatThrownBy(provider::current)
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("autenticado");
    }

    private static void authenticate(UUID userId, List<GrantedAuthority> authorities) {
        var principal = new PosUserDetails(
                userId, "test-user", "hash", authorities, null, 0L);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        principal, null, principal.getAuthorities()));
    }
}
