package co.posinvent.infrastructure.adapters.out.security;

import co.posinvent.infrastructure.adapters.out.persistence.UserJpaRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserJpaRepository userRepo;

    public UserDetailsServiceImpl(UserJpaRepository userRepo) {
        this.userRepo = userRepo;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        var entity = userRepo.findByUsernameAndActiveTrue(username)
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado: " + username));

        return new PosUserDetails(
                entity.getId(),
                entity.getUsername(),
                entity.getPasswordHash(),
                List.of(new SimpleGrantedAuthority("ROLE_" + entity.getRole().getName())),
                entity.getEmployee() != null ? entity.getEmployee().getId() : null
        );
    }
}
