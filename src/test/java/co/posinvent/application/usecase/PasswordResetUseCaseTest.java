package co.posinvent.application.usecase;

import co.posinvent.application.dto.ForgotPasswordRequest;
import co.posinvent.application.dto.SetPasswordRequest;
import co.posinvent.domain.exception.BusinessException;
import co.posinvent.infrastructure.adapters.out.persistence.PasswordResetTokenEntity;
import co.posinvent.infrastructure.adapters.out.persistence.PasswordResetTokenJpaRepository;
import co.posinvent.infrastructure.adapters.out.persistence.UserEntity;
import co.posinvent.infrastructure.adapters.out.persistence.UserJpaRepository;
import co.posinvent.infrastructure.service.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PasswordResetUseCaseTest {

    @Mock private UserJpaRepository userJpaRepository;
    @Mock private PasswordResetTokenJpaRepository tokenJpaRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private EmailService emailService;

    private PasswordResetUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new PasswordResetUseCase(
                userJpaRepository, tokenJpaRepository, passwordEncoder, emailService);
    }

    @Test
    void generateTokenStoresHashRevokesPreviousTokensAndEmailsRawToken() {
        var userId = UUID.randomUUID();
        var user = user(userId, true);
        when(userJpaRepository.findById(userId)).thenReturn(Optional.of(user));
        when(tokenJpaRepository.save(any(PasswordResetTokenEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        var rawToken = useCase.generateToken(userId);

        var saved = ArgumentCaptor.forClass(PasswordResetTokenEntity.class);
        InOrder order = inOrder(tokenJpaRepository);
        order.verify(tokenJpaRepository).deleteByUser_IdAndUsedFalse(userId);
        order.verify(tokenJpaRepository).save(saved.capture());

        assertThat(saved.getValue().getTokenHash()).isEqualTo(sha256Hex(rawToken));
        assertThat(saved.getValue().getTokenHash()).isNotEqualTo(rawToken);
        assertThat(saved.getValue().getTokenHash()).hasSize(64);
        assertThat(saved.getValue().getUser()).isSameAs(user);

        verify(emailService).sendSetPasswordInvitation(
                userId, user.getEmail(), user.getFullName(), rawToken);
    }

    @Test
    void forgotPasswordStoresHashRevokesPreviousTokensAndEmailsRawToken() {
        var userId = UUID.randomUUID();
        var user = user(userId, true);
        when(userJpaRepository.findByUsername(user.getEmail())).thenReturn(Optional.of(user));
        when(tokenJpaRepository.save(any(PasswordResetTokenEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        useCase.forgotPassword(new ForgotPasswordRequest(user.getEmail()));

        var saved = ArgumentCaptor.forClass(PasswordResetTokenEntity.class);
        InOrder order = inOrder(tokenJpaRepository);
        order.verify(tokenJpaRepository).deleteByUser_IdAndUsedFalse(userId);
        order.verify(tokenJpaRepository).save(saved.capture());

        var rawToken = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendPasswordReset(
                org.mockito.ArgumentMatchers.eq(userId),
                org.mockito.ArgumentMatchers.eq(user.getEmail()),
                org.mockito.ArgumentMatchers.eq(user.getFullName()),
                rawToken.capture());

        assertThat(saved.getValue().getTokenHash()).isEqualTo(sha256Hex(rawToken.getValue()));
        assertThat(saved.getValue().getTokenHash()).isNotEqualTo(rawToken.getValue());
        assertThat(saved.getValue().getTokenHash()).hasSize(64);
    }

    @Test
    void forgotPasswordStaysSilentForUnknownUser() {
        when(userJpaRepository.findByUsername("nobody@example.com")).thenReturn(Optional.empty());
        when(userJpaRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        useCase.forgotPassword(new ForgotPasswordRequest("nobody@example.com"));

        verifyNoInteractions(tokenJpaRepository, emailService);
    }

    @Test
    void forgotPasswordStaysSilentForInactiveUser() {
        var user = user(UUID.randomUUID(), false);
        when(userJpaRepository.findByUsername(user.getEmail())).thenReturn(Optional.of(user));

        useCase.forgotPassword(new ForgotPasswordRequest(user.getEmail()));

        verifyNoInteractions(tokenJpaRepository, emailService);
    }

    @Test
    void setPasswordHashesIncomingTokenUpdatesPasswordAndRevokesSessions() {
        var rawToken = "raw-token-value";
        var hash = sha256Hex(rawToken);
        var user = user(UUID.randomUUID(), true);
        user.setPasswordHash("old-hash");
        user.setTokenVersion(3);

        var entity = new PasswordResetTokenEntity();
        entity.setUser(user);
        entity.setTokenHash(hash);
        entity.setExpiresAt(OffsetDateTime.now().plusHours(1));
        entity.setUsed(false);

        when(tokenJpaRepository.findByTokenHashAndUsedFalse(hash)).thenReturn(Optional.of(entity));
        when(passwordEncoder.encode("NewSecret123!")).thenReturn("encoded-new-hash");

        useCase.setPassword(new SetPasswordRequest(rawToken, "NewSecret123!"));

        assertThat(user.getPasswordHash()).isEqualTo("encoded-new-hash");
        assertThat(user.getTokenVersion()).isEqualTo(4L);
        assertThat(entity.isUsed()).isTrue();

        verify(tokenJpaRepository).findByTokenHashAndUsedFalse(hash);
        verify(userJpaRepository).save(user);
        verify(tokenJpaRepository).save(entity);
    }

    @Test
    void setPasswordRejectsUnknownHashedToken() {
        var rawToken = "unknown-raw-token";
        when(tokenJpaRepository.findByTokenHashAndUsedFalse(sha256Hex(rawToken)))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.setPassword(
                new SetPasswordRequest(rawToken, "NewSecret123!")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo("INVALID_TOKEN");

        verify(passwordEncoder, never()).encode(any());
        verify(userJpaRepository, never()).save(any());
    }

    private UserEntity user(UUID id, boolean active) {
        var user = new UserEntity();
        user.setId(id);
        user.setEmail("user-" + id + "@example.com");
        user.setFullName("Test User");
        user.setActive(active);
        return user;
    }

    private static String sha256Hex(String value) {
        try {
            var digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
