package co.posinvent.application.usecase;

import co.posinvent.application.dto.LoginRequest;
import co.posinvent.application.dto.LoginResponse;
import co.posinvent.infrastructure.adapters.out.persistence.UserJpaRepository;
import co.posinvent.infrastructure.config.JwtProperties;
import co.posinvent.infrastructure.config.JwtService;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthenticateUserUseCase {

    private final AuthenticationManager authManager;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;
    private final UserJpaRepository userRepo;

    public AuthenticateUserUseCase(
            AuthenticationManager authManager,
            JwtService jwtService,
            JwtProperties jwtProperties,
            UserJpaRepository userRepo
    ) {
        this.authManager = authManager;
        this.jwtService = jwtService;
        this.jwtProperties = jwtProperties;
        this.userRepo = userRepo;
    }

    @Transactional(readOnly = true)
    public LoginResponse execute(LoginRequest request) {
        authManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.username(), request.password())
        );

        var user = userRepo.findByUsernameAndActiveTrue(request.username())
                .orElseThrow();

        var token = jwtService.generateToken(
                user.getId(),
                user.getUsername(),
                user.getRole().getName(),
                user.getEmployee() != null ? user.getEmployee().getId() : null
        );

        return new LoginResponse(
                token,
                jwtProperties.expirationMs(),
                user.getRole().getName(),
                user.getId(),
                user.getFullName(),
                user.getEmployee() != null ? user.getEmployee().getId() : null
        );
    }
}
