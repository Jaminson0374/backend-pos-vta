package co.posinvent.integration;

import co.posinvent.infrastructure.adapters.out.security.PosUserDetails;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.domain.AuditorAware;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = {
        "spring.profiles.active=test",
        "spring.main.allow-bean-definition-overriding=true"
    }
)
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    protected static final UUID TEST_USER_ID = UUID.fromString("a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11");

    /**
     * Singleton container: started once and shared by every integration test class in the JVM.
     *
     * <p>Deliberately NOT managed via {@code @Testcontainers} / {@code @Container}: those stop the
     * container after each test class, which leaves Spring's cached application contexts (reused by
     * the next class) pointing at a dead port ({@code Connection refused}).</p>
     */
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("posinvent_test")
            .withUsername("posinvent_test")
            .withPassword("posinvent_test");

    static {
        postgres.start();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    protected EntityManager em;

    @Autowired
    protected PlatformTransactionManager transactionManager;

    /**
     * Seeds a {@code users} row with {@link #TEST_USER_ID} so the {@code created_by}/{@code updated_by}
     * audit columns (FK to {@code users(id)}, see V93) can be populated by the auditor in tests.
     */
    @BeforeEach
    void ensureAuditorUserExists() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                em.createNativeQuery("""
                        INSERT INTO users (id, username, password_hash, email, full_name, role_id, is_active)
                        SELECT CAST(:id AS uuid), 'audit-test-user', 'n/a',
                               'audit-test-user@example.com', 'Audit Test User', r.id, true
                        FROM roles r WHERE r.name = 'ADMIN'
                        ON CONFLICT (id) DO NOTHING
                        """)
                        .setParameter("id", TEST_USER_ID.toString())
                        .executeUpdate());
    }

    /**
     * Simulates an authenticated user so the production {@code AuditorAwareImpl} resolves
     * {@code @CreatedBy}/{@code @LastModifiedBy}. The nested {@code @TestConfiguration} of this
     * superclass is not reliably applied to subclass test contexts, so the auditor is driven
     * through the real security context instead.
     */
    @BeforeEach
    void authenticateAuditor() {
        var principal = new PosUserDetails(
                TEST_USER_ID, "test-user", "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")), null);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    @AfterEach
    void clearAuditor() {
        SecurityContextHolder.clearContext();
    }

    @TestConfiguration
    static class TestAuditConfig {
        @Bean("auditorAwareImpl")
        @Primary
        AuditorAware<UUID> testAuditorAware() {
            return () -> Optional.of(TEST_USER_ID);
        }
    }
}
