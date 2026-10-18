package co.posinvent.infrastructure.config;

import co.posinvent.infrastructure.adapters.out.security.PosUserDetails;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthFilter.class);

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;

    public JwtAuthFilter(JwtService jwtService, UserDetailsService userDetailsService) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        final String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            filterChain.doFilter(request, response);
            return;
        }

        final String token = authHeader.substring(7);

        final Claims claims;
        try {
            claims = jwtService.extractAllClaims(token);
        } catch (Exception e) {
            // Includes expired, malformed and badly signed tokens.
            log.debug("Rejected JWT: token could not be parsed ({})", e.getMessage());
            filterChain.doFilter(request, response);
            return;
        }

        final String subject = claims.getSubject();
        final Object uidClaim = claims.get("uid");
        final Object tokenVersionClaim = claims.get("token_version");

        if (subject == null || subject.isBlank()
                || !(uidClaim instanceof String uidValue)
                || !(tokenVersionClaim instanceof Number versionValue)) {
            // Fail closed: legacy tokens without token_version force a re-login.
            log.debug("Rejected JWT: missing sub/uid/token_version claims");
            filterChain.doFilter(request, response);
            return;
        }

        final UUID userId;
        try {
            userId = UUID.fromString(uidValue);
        } catch (IllegalArgumentException e) {
            log.debug("Rejected JWT: malformed uid claim");
            filterChain.doFilter(request, response);
            return;
        }

        try {
            var userDetails = userDetailsService.loadUserByUsername(subject);
            if (userDetails instanceof PosUserDetails posUserDetails
                    && userId.equals(posUserDetails.userId())
                    && versionValue.longValue() == posUserDetails.tokenVersion()) {
                var authToken = new UsernamePasswordAuthenticationToken(
                        userDetails, null, userDetails.getAuthorities()
                );
                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authToken);
            } else {
                log.debug("Rejected JWT: user or token version mismatch for subject={}", subject);
            }
        } catch (UsernameNotFoundException e) {
            // Unknown or inactive user: leave the request unauthenticated (401).
            log.debug("Rejected JWT: unknown or inactive user subject={}", subject);
        } catch (Exception e) {
            log.debug("Rejected JWT: could not load user subject={} ({})", subject, e.getMessage());
        }

        filterChain.doFilter(request, response);
    }
}
