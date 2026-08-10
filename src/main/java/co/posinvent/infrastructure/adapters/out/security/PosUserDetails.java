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
        UUID employeeId
) implements UserDetails {

    @Override public Collection<? extends GrantedAuthority> getAuthorities() { return authorities; }
    @Override public String getPassword()  { return passwordHash; }
    @Override public String getUsername()  { return username; }
}
