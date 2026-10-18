package co.posinvent.integration;

import co.posinvent.application.dto.SetPasswordRequest;
import co.posinvent.application.dto.UserRequest;
import co.posinvent.application.usecase.PasswordResetUseCase;
import co.posinvent.application.usecase.UserUseCase;
import co.posinvent.domain.model.ThirdParty.PersonType;
import co.posinvent.domain.model.ThirdParty.TaxRegime;
import co.posinvent.domain.model.ThirdParty.ThirdPartyType;
import co.posinvent.infrastructure.adapters.out.persistence.PasswordResetTokenEntity;
import co.posinvent.infrastructure.adapters.out.persistence.PasswordResetTokenJpaRepository;
import co.posinvent.infrastructure.adapters.out.persistence.RoleEntity;
import co.posinvent.infrastructure.adapters.out.persistence.RoleJpaRepository;
import co.posinvent.infrastructure.adapters.out.persistence.ThirdPartyEntity;
import co.posinvent.infrastructure.adapters.out.persistence.UserEntity;
import co.posinvent.infrastructure.adapters.out.persistence.UserJpaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end proof of token-version revocation (S2a).
 *
 * <p>A password reset and an active-to-inactive transition must bump
 * {@code users.token_version}, which invalidates every previously issued JWT.</p>
 */
class TokenVersionRevocationIntegrationTest extends AbstractIntegrationTest {

    private static final String OLD_PASSWORD = "OldSecret123!";
    private static final String NEW_PASSWORD = "NewSecret123!";

    @Autowired private PasswordResetUseCase passwordResetUseCase;
    @Autowired private UserUseCase userUseCase;
    @Autowired private UserJpaRepository userJpaRepository;
    @Autowired private PasswordResetTokenJpaRepository tokenJpaRepository;
    @Autowired private RoleJpaRepository roleJpaRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @Test
    void passwordResetIncrementsTokenVersionAndReplacesPasswordHash() {
        var oldHash = passwordEncoder.encode(OLD_PASSWORD);
        var suffix = UUID.randomUUID().toString().substring(0, 8);
        var tokenValue = "token-" + UUID.randomUUID();
        var userIdHolder = new UUID[1];

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            var user = new UserEntity();
            user.setUsername("revocation-reset-" + suffix);
            user.setPasswordHash(oldHash);
            user.setEmail("revocation-reset-" + suffix + "@example.com");
            user.setFullName("Revocation Reset User");
            user.setRole(roleByName("CONTADOR"));
            user.setActive(true);
            user.setCreatedAt(OffsetDateTime.now());
            user.setUpdatedAt(OffsetDateTime.now());
            userJpaRepository.save(user);

            var resetToken = new PasswordResetTokenEntity();
            resetToken.setUser(user);
            resetToken.setToken(tokenValue);
            resetToken.setExpiresAt(OffsetDateTime.now().plusHours(1));
            resetToken.setUsed(false);
            tokenJpaRepository.save(resetToken);

            userIdHolder[0] = user.getId();
        });

        passwordResetUseCase.setPassword(new SetPasswordRequest(tokenValue, NEW_PASSWORD));

        var reloaded = userJpaRepository.findById(userIdHolder[0]).orElseThrow();
        assertThat(reloaded.getTokenVersion()).isEqualTo(1L);
        assertThat(reloaded.getPasswordHash()).isNotEqualTo(oldHash);
        assertThat(passwordEncoder.matches(NEW_PASSWORD, reloaded.getPasswordHash())).isTrue();
    }

    @Test
    void deactivatingUserIncrementsTokenVersion() {
        var suffix = UUID.randomUUID().toString().substring(0, 8);
        var userIdHolder = new UUID[1];
        var employeeIdHolder = new UUID[1];
        var roleIdHolder = new UUID[1];
        var email = "revocation-deactivate-" + suffix + "@example.com";

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            var employee = new ThirdPartyEntity();
            employee.setNumIdentification("REV-EMP-" + suffix);
            employee.setName("Revocation Employee " + suffix);
            employee.setType(ThirdPartyType.EMPLOYEE);
            employee.setPersonType(PersonType.JURIDICA);
            employee.setTaxRegime(TaxRegime.ORDINARIO);
            employee.setTaxResponsibilities(List.of());
            employee.setActive(true);
            employee.setCreditLimit(BigDecimal.ZERO);
            em.persist(employee);

            var role = roleByName("CONTADOR");
            var user = new UserEntity();
            user.setUsername("revocation-deactivate-" + suffix);
            user.setPasswordHash("n/a");
            user.setEmail(email);
            user.setFullName("Revocation Deactivate User");
            user.setRole(role);
            user.setActive(true);
            user.setEmployee(employee);
            user.setCreatedAt(OffsetDateTime.now());
            user.setUpdatedAt(OffsetDateTime.now());
            userJpaRepository.save(user);

            userIdHolder[0] = user.getId();
            employeeIdHolder[0] = employee.getId();
            roleIdHolder[0] = role.getId();
        });

        // The security context set by AbstractIntegrationTest belongs to a different admin,
        // so this is not a self-deactivation.
        userUseCase.update(
                userIdHolder[0],
                new UserRequest(employeeIdHolder[0], email, roleIdHolder[0], false)
        );

        var reloaded = userJpaRepository.findById(userIdHolder[0]).orElseThrow();
        assertThat(reloaded.isActive()).isFalse();
        assertThat(reloaded.getTokenVersion()).isEqualTo(1L);
    }

    private RoleEntity roleByName(String name) {
        return roleJpaRepository.findAll().stream()
                .filter(role -> name.equals(role.getName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing seeded role " + name));
    }
}
