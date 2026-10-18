package co.posinvent.infrastructure.config;

import co.posinvent.application.service.JwtBlacklistService;
import co.posinvent.infrastructure.adapters.out.persistence.RoleEntity;
import co.posinvent.infrastructure.adapters.out.persistence.UserEntity;
import co.posinvent.infrastructure.adapters.out.persistence.UserJpaRepository;
import co.posinvent.infrastructure.adapters.out.security.UserDetailsServiceImpl;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * End-to-end filter test using the real {@link JwtService} and
 * {@link UserDetailsServiceImpl} over a mocked repository.
 *
 * <p>Unlike {@link JwtAuthFilterTest}, this exercises the actual claim-to-principal
 * mapping instead of stubbing the user lookup.</p>
 */
class JwtAuthFilterUserDetailsIntegrationTest {

    private static final String SECRET =
            "a-valid-random-jwt-secret-with-more-than-sixty-four-characters-1234";
    private static final long EXPIRATION_MS = 86_400_000L;
    private static final UUID USER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final String USERNAME = "integration-user@posinvent.co";
    private static final long INITIAL_VERSION = 4L;

    private JwtService jwtService;
    private UserJpaRepository userRepository;
    private JwtAuthFilter filter;
    private UserEntity user;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        jwtService = new JwtService(new JwtProperties(SECRET, EXPIRATION_MS));

        var role = new RoleEntity();
        role.setName("ADMIN");

        user = new UserEntity();
        user.setId(USER_ID);
        user.setUsername(USERNAME);
        user.setPasswordHash("password-hash");
        user.setEmail("integration-user@posinvent.co");
        user.setFullName("Integration User");
        user.setRole(role);
        user.setActive(true);
        user.setTokenVersion(INITIAL_VERSION);

        userRepository = mock(UserJpaRepository.class);
        when(userRepository.findByUsernameAndActiveTrue(USERNAME)).thenReturn(Optional.of(user));

        var userDetailsService = new UserDetailsServiceImpl(userRepository);
        var blacklistService = mock(JwtBlacklistService.class);
        when(blacklistService.isRevoked(any())).thenReturn(false);
        filter = new JwtAuthFilter(jwtService, userDetailsService, blacklistService);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void matchingUidAndTokenVersionAuthenticates() throws Exception {
        var token = jwtService.generateToken(USER_ID, USERNAME, "ADMIN", null, INITIAL_VERSION);

        var chain = mock(FilterChain.class);
        runFilter(token, chain);

        var authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNotNull();
        assertThat(authentication.isAuthenticated()).isTrue();
        verify(chain).doFilter(any(), any());
    }

    @Test
    void bumpedTokenVersionRejectsPreviouslyIssuedToken() throws Exception {
        var token = jwtService.generateToken(USER_ID, USERNAME, "ADMIN", null, INITIAL_VERSION);

        user.setTokenVersion(INITIAL_VERSION + 1);

        var chain = mock(FilterChain.class);
        runFilter(token, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain).doFilter(any(), any());
    }

    @Test
    void tokenForDifferentUserIdRejectsSubjectWithMatchingUsername() throws Exception {
        var token = jwtService.generateToken(
                UUID.randomUUID(), USERNAME, "ADMIN", null, INITIAL_VERSION);

        var chain = mock(FilterChain.class);
        runFilter(token, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain).doFilter(any(), any());
    }

    private void runFilter(String token, FilterChain chain) throws Exception {
        var request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        filter.doFilter(request, new MockHttpServletResponse(), chain);
    }
}
