package co.posinvent.application.usecase;

import co.posinvent.domain.exception.BusinessException;
import co.posinvent.domain.model.DiscountAuthorization;
import co.posinvent.domain.repository.DiscountAuthorizationRepository;
import co.posinvent.infrastructure.adapters.out.persistence.RoleEntity;
import co.posinvent.infrastructure.adapters.out.persistence.UserEntity;
import co.posinvent.infrastructure.adapters.out.persistence.UserJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the single-use discount authorization token (M2 / WU-27):
 * issuance validates ADMIN credentials and the two-minute expiry; consumption is
 * single use and rejects missing / used / expired tokens.
 */
@ExtendWith(MockitoExtension.class)
class AuthorizeDiscountUseCaseTest {

    @Mock private AuthenticationManager authManager;
    @Mock private UserJpaRepository userRepo;
    @Mock private DiscountAuthorizationRepository authorizationRepo;

    private AuthorizeDiscountUseCase useCase;

    private static final UUID AUTH_ID = UUID.randomUUID();
    private static final UUID REQUESTED_BY = UUID.randomUUID();
    private static final UUID CASH_REGISTER_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        useCase = new AuthorizeDiscountUseCase(authManager, userRepo, authorizationRepo);
    }

    private static UserEntity user(String username, String role) {
        var roleEntity = new RoleEntity();
        roleEntity.setName(role);
        var entity = new UserEntity();
        entity.setId(UUID.randomUUID());
        entity.setUsername(username);
        entity.setActive(true);
        entity.setRole(roleEntity);
        return entity;
    }

    private static DiscountAuthorization token(boolean used, OffsetDateTime expiresAt) {
        return new DiscountAuthorization(
                AUTH_ID, REQUESTED_BY, UUID.randomUUID(), CASH_REGISTER_ID, expiresAt, used, OffsetDateTime.now());
    }

    @Test
    void authorizeWithAdminCredentialsIssuesUnusedTwoMinuteToken() {
        var admin = user("admin1", "ADMIN");
        when(authManager.authenticate(any())).thenReturn(
                new UsernamePasswordAuthenticationToken("admin1", "secret"));
        when(userRepo.findByUsernameAndActiveTrue("admin1")).thenReturn(Optional.of(admin));
        when(authorizationRepo.save(any())).thenAnswer(inv -> {
            DiscountAuthorization a = inv.getArgument(0);
            return new DiscountAuthorization(
                    AUTH_ID, a.requestedBy(), a.authorizedBy(), a.cashRegisterId(),
                    a.expiresAt(), a.used(), OffsetDateTime.now());
        });

        var before = OffsetDateTime.now();
        var response = useCase.authorize("admin1", "secret", CASH_REGISTER_ID, REQUESTED_BY);

        assertThat(response.authorizationId()).isEqualTo(AUTH_ID);
        assertThat(response.authorizedBy()).isEqualTo("admin1");
        // ~2 minutes from now, never longer.
        assertThat(response.expiresAt()).isAfter(before.plusSeconds(90));
        assertThat(response.expiresAt()).isBefore(before.plusSeconds(150));

        var captor = ArgumentCaptor.forClass(DiscountAuthorization.class);
        verify(authorizationRepo).save(captor.capture());
        var saved = captor.getValue();
        assertThat(saved.used()).isFalse();
        assertThat(saved.authorizedBy()).isEqualTo(admin.getId());
        assertThat(saved.requestedBy()).isEqualTo(REQUESTED_BY);
        assertThat(saved.cashRegisterId()).isEqualTo(CASH_REGISTER_ID);
    }

    @Test
    void authorizeWithInvalidCredentialsFailsWithoutIssuingToken() {
        when(authManager.authenticate(any())).thenThrow(new BadCredentialsException("bad"));

        var thrown = catchThrowable(() -> useCase.authorize("admin1", "wrong", null, REQUESTED_BY));

        assertThat(thrown).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) thrown).getErrorCode())
                .isEqualTo("DISCOUNT_AUTHORIZATION_INVALID_CREDENTIALS");
        verifyNoInteractions(authorizationRepo);
    }

    @Test
    void authorizeWithNonAdminUserIsForbidden() {
        when(authManager.authenticate(any())).thenReturn(
                new UsernamePasswordAuthenticationToken("cashier1", "secret"));
        when(userRepo.findByUsernameAndActiveTrue("cashier1"))
                .thenReturn(Optional.of(user("cashier1", "CAJERO")));

        var thrown = catchThrowable(() -> useCase.authorize("cashier1", "secret", null, REQUESTED_BY));

        assertThat(thrown).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) thrown).getErrorCode())
                .isEqualTo("DISCOUNT_AUTHORIZATION_FORBIDDEN");
        verifyNoInteractions(authorizationRepo);
    }

    @Test
    void consumeValidTokenMarksUsedAndReturnsApprover() {
        var admin = user("admin1", "ADMIN");
        var token = new DiscountAuthorization(
                AUTH_ID, REQUESTED_BY, admin.getId(), CASH_REGISTER_ID,
                OffsetDateTime.now().plusMinutes(2), false, OffsetDateTime.now());
        when(authorizationRepo.findById(AUTH_ID)).thenReturn(Optional.of(token));

        var approver = useCase.consume(AUTH_ID);

        assertThat(approver).isEqualTo(admin.getId());
        verify(authorizationRepo).markUsed(AUTH_ID);
    }

    @Test
    void consumeAlreadyUsedTokenIsRejected() {
        when(authorizationRepo.findById(AUTH_ID))
                .thenReturn(Optional.of(token(true, OffsetDateTime.now().plusMinutes(2))));

        var thrown = catchThrowable(() -> useCase.consume(AUTH_ID));

        assertThat(thrown).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) thrown).getErrorCode())
                .isEqualTo("DISCOUNT_AUTHORIZATION_REQUIRED");
        verify(authorizationRepo, org.mockito.Mockito.never()).markUsed(any());
    }

    @Test
    void consumeExpiredTokenIsRejected() {
        when(authorizationRepo.findById(AUTH_ID))
                .thenReturn(Optional.of(token(false, OffsetDateTime.now().minusSeconds(1))));

        var thrown = catchThrowable(() -> useCase.consume(AUTH_ID));

        assertThat(thrown).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) thrown).getErrorCode())
                .isEqualTo("DISCOUNT_AUTHORIZATION_REQUIRED");
        verify(authorizationRepo, org.mockito.Mockito.never()).markUsed(any());
    }

    @Test
    void consumeUnknownTokenIsRejected() {
        when(authorizationRepo.findById(AUTH_ID)).thenReturn(Optional.empty());

        var thrown = catchThrowable(() -> useCase.consume(AUTH_ID));

        assertThat(thrown).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) thrown).getErrorCode())
                .isEqualTo("DISCOUNT_AUTHORIZATION_REQUIRED");
    }
}
