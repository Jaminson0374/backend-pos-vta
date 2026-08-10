package co.posinvent.application.usecase;

import co.posinvent.application.dto.ForgotPasswordRequest;
import co.posinvent.application.dto.SetPasswordRequest;
import co.posinvent.domain.exception.BusinessException;
import co.posinvent.infrastructure.adapters.out.persistence.PasswordResetTokenEntity;
import co.posinvent.infrastructure.adapters.out.persistence.PasswordResetTokenJpaRepository;
import co.posinvent.infrastructure.adapters.out.persistence.UserJpaRepository;
import co.posinvent.infrastructure.service.EmailService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.UUID;

@Service
public class PasswordResetUseCase {

    private final UserJpaRepository userJpaRepository;
    private final PasswordResetTokenJpaRepository tokenJpaRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int TOKEN_EXPIRY_HOURS = 24;
    private static final int FORGOT_TOKEN_EXPIRY_HOURS = 1;

    public PasswordResetUseCase(
            UserJpaRepository userJpaRepository,
            PasswordResetTokenJpaRepository tokenJpaRepository,
            PasswordEncoder passwordEncoder,
            EmailService emailService) {
        this.userJpaRepository = userJpaRepository;
        this.tokenJpaRepository = tokenJpaRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
    }

    /**
     * Generates a password reset token and sends invitation email.
     * Called during user creation.
     */
    @Transactional
    public String generateToken(UUID userId) {
        var user = userJpaRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("USER_NOT_FOUND", "Usuario no encontrado."));

        var tokenStr = generateRandomToken();
        var now = OffsetDateTime.now();

        var entity = new PasswordResetTokenEntity();
        entity.setUser(user);
        entity.setToken(tokenStr);
        entity.setExpiresAt(now.plusHours(TOKEN_EXPIRY_HOURS));
        entity.setUsed(false);
        tokenJpaRepository.save(entity);

        emailService.sendSetPasswordInvitation(user.getEmail(), user.getFullName(), tokenStr);

        return tokenStr;
    }

    /**
     * Validates token and sets the new password.
     */
    @Transactional
    public void setPassword(SetPasswordRequest request) {
        var tokenEntity = tokenJpaRepository.findByTokenAndUsedFalse(request.token())
                .orElseThrow(() -> new BusinessException("INVALID_TOKEN",
                        "El enlace no es válido o ya fue usado."));

        if (tokenEntity.getExpiresAt().isBefore(OffsetDateTime.now())) {
            throw new BusinessException("EXPIRED_TOKEN",
                    "El enlace expiró. Solicitá uno nuevo.");
        }

        var user = tokenEntity.getUser();
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userJpaRepository.save(user);

        tokenEntity.setUsed(true);
        tokenJpaRepository.save(tokenEntity);
    }

    /**
     * Initiates the "forgot password" flow.
     * Always returns success to avoid user enumeration.
     */
    @Transactional
    public void forgotPassword(ForgotPasswordRequest request) {
        var userOpt = userJpaRepository.findByUsername(request.email());

        // Also try by email if username didn't match
        var user = userOpt.isPresent()
                ? userOpt.get()
                : userJpaRepository.findByEmail(request.email()).orElse(null);

        if (user == null || !user.isActive()) {
            // Don't reveal whether user exists — silently succeed
            return;
        }

        var tokenStr = generateRandomToken();
        var now = OffsetDateTime.now();

        var entity = new PasswordResetTokenEntity();
        entity.setUser(user);
        entity.setToken(tokenStr);
        entity.setExpiresAt(now.plusHours(FORGOT_TOKEN_EXPIRY_HOURS));
        entity.setUsed(false);
        tokenJpaRepository.save(entity);

        emailService.sendPasswordReset(user.getEmail(), user.getFullName(), tokenStr);
    }

    private String generateRandomToken() {
        byte[] bytes = new byte[48];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
