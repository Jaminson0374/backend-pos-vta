package co.posinvent.infrastructure.adapters.in.rest;

import co.posinvent.application.usecase.CustomerStatementUseCase;
import co.posinvent.infrastructure.adapters.out.security.PosUserDetails;
import co.posinvent.infrastructure.config.JwtAuthFilter;
import co.posinvent.application.service.JwtBlacklistService;
import co.posinvent.infrastructure.config.JwtService;
import co.posinvent.infrastructure.config.SecurityConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.then;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice tests for the customer-statement role matrix. Only {@code ADMIN}, {@code CONTADOR} and
 * {@code CAJERO} may generate a statement.
 */
@WebMvcTest(controllers = CustomerStatementController.class)
@ActiveProfiles("test")
@Import({SecurityConfig.class, CustomerStatementControllerSecurityTest.JwtFilterTestConfig.class})
class CustomerStatementControllerSecurityTest {

    private static final UUID CLIENT_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CustomerStatementUseCase useCase;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private UserDetailsService userDetailsService;

    @MockitoBean
    private JwtBlacklistService jwtBlacklistService;

    @TestConfiguration
    static class JwtFilterTestConfig {
        @Bean
        JwtAuthFilter jwtAuthFilter(JwtService jwtService, UserDetailsService userDetailsService,
                                    JwtBlacklistService jwtBlacklistService) {
            return new JwtAuthFilter(jwtService, userDetailsService, jwtBlacklistService);
        }
    }

    private static RequestPostProcessor asRole(String role) {
        var principal = new PosUserDetails(
                UUID.randomUUID(),
                "user-" + role.toLowerCase(),
                "irrelevant-hash",
                List.of(new SimpleGrantedAuthority("ROLE_" + role)),
                null,
                0L
        );
        return authentication(new UsernamePasswordAuthenticationToken(
                principal, null, principal.getAuthorities()));
    }

    private void expectStatementAllowed(String role) throws Exception {
        mockMvc.perform(get("/api/v1/customer-statements/{clientId}", CLIENT_ID)
                        .param("from", "2026-01-01")
                        .param("to", "2026-01-31")
                        .with(asRole(role)))
                .andExpect(status().isOk());

        then(useCase).should().generate(any(), any(), any());
    }

    @Test
    @DisplayName("ADMIN can generate a customer statement (GET -> 200)")
    void adminCanGenerateStatement() throws Exception {
        expectStatementAllowed("ADMIN");
    }

    @Test
    @DisplayName("CONTADOR can generate a customer statement (GET -> 200)")
    void contadorCanGenerateStatement() throws Exception {
        expectStatementAllowed("CONTADOR");
    }

    @Test
    @DisplayName("CAJERO can generate a customer statement (GET -> 200)")
    void cajeroCanGenerateStatement() throws Exception {
        expectStatementAllowed("CAJERO");
    }

    @Test
    @DisplayName("ALMACENISTA cannot generate a customer statement (GET -> 403)")
    void almacenistaCannotGenerateStatement() throws Exception {
        mockMvc.perform(get("/api/v1/customer-statements/{clientId}", CLIENT_ID)
                        .param("from", "2026-01-01")
                        .param("to", "2026-01-31")
                        .with(asRole("ALMACENISTA")))
                .andExpect(status().isForbidden());

        then(useCase).shouldHaveNoInteractions();
    }
}
