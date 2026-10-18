package co.posinvent.infrastructure.config;

import co.posinvent.infrastructure.adapters.out.security.PosUserDetails;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JwtAuthFilterTest {

    private static final String SECRET =
            "a-valid-random-jwt-secret-with-more-than-sixty-four-characters-1234";
    private static final long EXPIRATION_MS = 86_400_000L;
    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final String USERNAME = "admin@posinvent.co";
    private static final long CURRENT_VERSION = 3L;

    private JwtService jwtService;
    private UserDetailsService userDetailsService;
    private JwtAuthFilter filter;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        jwtService = new JwtService(new JwtProperties(SECRET, EXPIRATION_MS));
        userDetailsService = mock(UserDetailsService.class);
        filter = new JwtAuthFilter(jwtService, userDetailsService);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void validTokenWithMatchingVersionAuthenticates() throws Exception {
        when(userDetailsService.loadUserByUsername(USERNAME))
                .thenReturn(principal(CURRENT_VERSION));
        var token = jwtService.generateToken(USER_ID, USERNAME, "ADMIN", null, CURRENT_VERSION);

        var chain = mock(FilterChain.class);
        runFilter(token, chain);

        var authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNotNull();
        assertThat(authentication.isAuthenticated()).isTrue();
        assertThat(authentication.getPrincipal()).isInstanceOf(PosUserDetails.class);
        assertThat(((PosUserDetails) authentication.getPrincipal()).tokenVersion())
                .isEqualTo(CURRENT_VERSION);
        verify(chain).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void staleTokenVersionDoesNotAuthenticateButContinuesChain() throws Exception {
        when(userDetailsService.loadUserByUsername(USERNAME))
                .thenReturn(principal(CURRENT_VERSION));
        var staleVersion = CURRENT_VERSION - 1;
        var token = jwtService.generateToken(USER_ID, USERNAME, "ADMIN", null, staleVersion);

        var chain = mock(FilterChain.class);
        runFilter(token, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void tokenWithoutTokenVersionClaimDoesNotAuthenticate() throws Exception {
        when(userDetailsService.loadUserByUsername(USERNAME))
                .thenReturn(principal(CURRENT_VERSION));
        var legacyToken = tokenWithoutTokenVersionClaim();

        var chain = mock(FilterChain.class);
        runFilter(legacyToken, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void inactiveOrUnknownUserDoesNotAuthenticateButContinuesChain() throws Exception {
        when(userDetailsService.loadUserByUsername(USERNAME))
                .thenThrow(new UsernameNotFoundException("Usuario no encontrado: " + USERNAME));
        var token = jwtService.generateToken(USER_ID, USERNAME, "ADMIN", null, CURRENT_VERSION);

        var chain = mock(FilterChain.class);
        runFilter(token, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    private void runFilter(String token, FilterChain chain) throws Exception {
        var request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        filter.doFilter(request, new MockHttpServletResponse(), chain);
    }

    private static PosUserDetails principal(long tokenVersion) {
        return new PosUserDetails(
                USER_ID,
                USERNAME,
                "password-hash",
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")),
                null,
                tokenVersion
        );
    }

    private static String tokenWithoutTokenVersionClaim() {
        var key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .subject(USERNAME)
                .claim("uid", USER_ID.toString())
                .claim("role", "ADMIN")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + EXPIRATION_MS))
                .signWith(key, Jwts.SIG.HS512)
                .compact();
    }
}
