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
    void resolvesUserIdAndSingleRoleFromPosUserDetails() {
        authenticate(USER_ID, List.of(new SimpleGrantedAuthority("ROLE_CAJERO")));

        var actor = provider.current();

        assertThat(actor.userId()).isEqualTo(USER_ID);
        assertThat(actor.roles()).containsExactly("CAJERO");
        assertThat(actor.hasRole("CAJERO")).isTrue();
        assertThat(actor.isAdmin()).isFalse();
        assertThat(actor.isContador()).isFalse();
        assertThat(actor.primaryRoleForDiscountPolicy()).isEqualTo("CAJERO");
    }

    @Test
    void ignoresNonRoleAuthorities() {
        authenticate(USER_ID, List.of(
                new SimpleGrantedAuthority("PERM_SOMETHING"),
                new SimpleGrantedAuthority("ROLE_ADMIN")));

        var actor = provider.current();

        assertThat(actor.roles()).containsExactly("ADMIN");
        assertThat(actor.isAdmin()).isTrue();
    }

    @Test
    void collectsEveryRoleRegardlessOfAuthorityOrder() {
        authenticate(USER_ID, List.of(
                new SimpleGrantedAuthority("ROLE_CAJERO"),
                new SimpleGrantedAuthority("ROLE_ADMIN")));

        var cajeroFirst = provider.current();

        authenticate(USER_ID, List.of(
                new SimpleGrantedAuthority("ROLE_ADMIN"),
                new SimpleGrantedAuthority("ROLE_CAJERO")));

        var adminFirst = provider.current();

        assertThat(cajeroFirst.isAdmin()).isTrue();
        assertThat(adminFirst.isAdmin()).isTrue();
        assertThat(cajeroFirst.roles()).containsExactlyInAnyOrder("CAJERO", "ADMIN");
        assertThat(adminFirst.roles()).containsExactlyInAnyOrder("CAJERO", "ADMIN");
        assertThat(cajeroFirst.primaryRoleForDiscountPolicy()).isEqualTo("ADMIN");
        assertThat(adminFirst.primaryRoleForDiscountPolicy()).isEqualTo("ADMIN");
    }

    @Test
    void answersHasAnyRole() {
        authenticate(USER_ID, List.of(new SimpleGrantedAuthority("ROLE_CONTADOR")));

        var actor = provider.current();

        assertThat(actor.hasAnyRole("ADMIN", "CONTADOR")).isTrue();
        assertThat(actor.hasAnyRole("ADMIN", "CAJERO")).isFalse();
    }

    @Test
    void normalizesRolesToUppercase() {
        authenticate(USER_ID, List.of(new SimpleGrantedAuthority("ROLE_cajero")));

        var actor = provider.current();

        assertThat(actor.roles()).containsExactly("CAJERO");
        assertThat(actor.hasRole("cajero")).isTrue();
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
