package co.posinvent.application.usecase;

import co.posinvent.application.dto.DiscountAuthorizationResponse;
import co.posinvent.domain.exception.BusinessException;
import co.posinvent.domain.model.DiscountAuthorization;
import co.posinvent.domain.repository.DiscountAuthorizationRepository;
import co.posinvent.infrastructure.adapters.out.persistence.UserJpaRepository;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Issues and consumes single-use discount authorization tokens (M2 / WU-27).
 *
 * <p>Issuance ({@link #authorize}) validates the administrator against the same
 * {@link AuthenticationManager} used by login and requires the {@code ADMIN} role.
 * The token lives for {@value #TOKEN_TTL_MINUTES} minutes and can be consumed once
 * by {@link #consume}, which the checkout calls before persisting the invoice.
 * Credentials never leave this use case: the checkout carries only the token id.</p>
 */
@Service
public class AuthorizeDiscountUseCase {

    /** Short-lived token: the admin approves while standing next to the cashier. */
    public static final int TOKEN_TTL_MINUTES = 2;

    private static final String ROLE_ADMIN = "ADMIN";
    private static final String INVALID_CREDENTIALS = "DISCOUNT_AUTHORIZATION_INVALID_CREDENTIALS";
    private static final String FORBIDDEN = "DISCOUNT_AUTHORIZATION_FORBIDDEN";
    private static final String REQUIRED = "DISCOUNT_AUTHORIZATION_REQUIRED";
    private static final String REQUIRED_MESSAGE =
            "El descuento requiere autorización de un administrador";

    private final AuthenticationManager authManager;
    private final UserJpaRepository userRepo;
    private final DiscountAuthorizationRepository authorizationRepo;

    public AuthorizeDiscountUseCase(
            AuthenticationManager authManager,
            UserJpaRepository userRepo,
            DiscountAuthorizationRepository authorizationRepo
    ) {
        this.authManager = authManager;
        this.userRepo = userRepo;
        this.authorizationRepo = authorizationRepo;
    }

    /**
     * Validates the administrator credentials and issues a single-use token.
     *
     * <p>Invalid credentials are surfaced as {@code DISCOUNT_AUTHORIZATION_INVALID_CREDENTIALS}
     * (mapped to 409 by {@code GlobalExceptionHandler}) instead of letting Spring return a
     * bare 401, so the POS can distinguish a failed authorization from a missing session.</p>
     */
    @Transactional
    public DiscountAuthorizationResponse authorize(
            String username, String password, UUID cashRegisterId, UUID requestedBy) {
        try {
            authManager.authenticate(new UsernamePasswordAuthenticationToken(username, password));
        } catch (AuthenticationException ex) {
            throw new BusinessException(INVALID_CREDENTIALS, "Credenciales inválidas");
        }

        var admin = userRepo.findByUsernameAndActiveTrue(username)
                .orElseThrow(() -> new BusinessException(INVALID_CREDENTIALS, "Credenciales inválidas"));

        if (admin.getRole() == null || !ROLE_ADMIN.equalsIgnoreCase(admin.getRole().getName())) {
            throw new BusinessException(FORBIDDEN, "Solo un administrador puede autorizar descuentos");
        }

        var expiresAt = OffsetDateTime.now().plusMinutes(TOKEN_TTL_MINUTES);
        var saved = authorizationRepo.save(new DiscountAuthorization(
                null, requestedBy, admin.getId(), cashRegisterId, expiresAt, false, null));

        return new DiscountAuthorizationResponse(saved.id(), saved.expiresAt(), admin.getUsername());
    }

    /**
     * Consumes the token and returns the approving administrator's id. Single use:
     * a missing, already-used or expired token is rejected with
     * {@code DISCOUNT_AUTHORIZATION_REQUIRED}.
     */
    @Transactional
    public UUID consume(UUID authorizationId) {
        var authorization = (authorizationId == null
                ? Optional.<DiscountAuthorization>empty()
                : authorizationRepo.findById(authorizationId))
                .orElseThrow(() -> new BusinessException(REQUIRED, REQUIRED_MESSAGE));

        if (authorization.used()
                || authorization.expiresAt() == null
                || !authorization.expiresAt().isAfter(OffsetDateTime.now())) {
            throw new BusinessException(REQUIRED, REQUIRED_MESSAGE);
        }

        // Atomic single-use flip: a concurrent checkout that already consumed the token
        // makes this return false, so the race cannot authorize two invoices.
        if (!authorizationRepo.markUsedIfUnused(authorization.id())) {
            throw new BusinessException(REQUIRED, REQUIRED_MESSAGE);
        }
        return authorization.authorizedBy();
    }
}
