package co.posinvent.infrastructure.adapters.out.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public record PosUserDetails(
        UUID userId,
        String username,
        String passwordHash,
        List<GrantedAuthority> authorities,
        UUID employeeId,
        long tokenVersion
) implements UserDetails {

    /**
     * Backwards-compatible constructor for callers that do not track a token version.
     * Defaults to 0 so previously issued tokens remain comparable during the rollout.
     */
    public PosUserDetails(
            UUID userId,
            String username,
            String passwordHash,
            List<GrantedAuthority> authorities,
            UUID employeeId
    ) {
        this(userId, username, passwordHash, authorities, employeeId, 0L);
    }

    @Override public Collection<? extends GrantedAuthority> getAuthorities() { return authorities; }
    @Override public String getPassword()  { return passwordHash; }
    @Override public String getUsername()  { return username; }
}
