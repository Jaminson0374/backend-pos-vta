package co.posinvent.infrastructure.adapters.in.rest;

import co.posinvent.application.dto.ForgotPasswordRequest;
import co.posinvent.application.dto.LoginRequest;
import co.posinvent.application.dto.LoginResponse;
import co.posinvent.application.dto.SetPasswordRequest;
import co.posinvent.application.usecase.AuthenticateUserUseCase;
import co.posinvent.application.usecase.LogoutUseCase;
import co.posinvent.application.usecase.PasswordResetUseCase;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthenticateUserUseCase authenticateUser;
    private final PasswordResetUseCase passwordResetUseCase;
    private final LogoutUseCase logoutUseCase;

    public AuthController(
            AuthenticateUserUseCase authenticateUser,
            PasswordResetUseCase passwordResetUseCase,
            LogoutUseCase logoutUseCase) {
        this.authenticateUser = authenticateUser;
        this.passwordResetUseCase = passwordResetUseCase;
        this.logoutUseCase = logoutUseCase;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authenticateUser.execute(request));
    }

    @PostMapping("/set-password")
    public ResponseEntity<Map<String, String>> setPassword(@Valid @RequestBody SetPasswordRequest request) {
        passwordResetUseCase.setPassword(request);
        return ResponseEntity.ok(Map.of("message", "Contraseña configurada exitosamente."));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<Map<String, String>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        passwordResetUseCase.forgotPassword(request);
        return ResponseEntity.ok(Map.of("message",
                "Si el email está registrado, recibirás un enlace para restablecer tu contraseña."));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        var token = authorization.substring(7);
        if (token.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        logoutUseCase.logout(token);
        return ResponseEntity.noContent().build();
    }
}
