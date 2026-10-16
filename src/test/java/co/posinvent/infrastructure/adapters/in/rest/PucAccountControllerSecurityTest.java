package co.posinvent.infrastructure.adapters.in.rest;

import co.posinvent.application.usecase.PucAccountUseCase;
import co.posinvent.infrastructure.adapters.out.security.PosUserDetails;
import co.posinvent.infrastructure.config.JwtAuthFilter;
import co.posinvent.infrastructure.config.JwtService;
import co.posinvent.infrastructure.config.SecurityConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
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

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice tests for the PUC read surface ({@code GET /api/v1/puc-accounts}, {@code /{id}},
 * {@code /tree}, {@code /check-code}).
 *
 * <p>M5: the chart of accounts is no longer exposed to any authenticated user. Every read
 * requires {@code ADMIN} or {@code CONTADOR}; a cashier or butcher gets 403 and the use case
 * is never reached.</p>
 */
@WebMvcTest(controllers = PucAccountController.class)
@ActiveProfiles("test")
@Import({SecurityConfig.class, PucAccountControllerSecurityTest.JwtFilterTestConfig.class})
class PucAccountControllerSecurityTest {

    private static final UUID ACCOUNT_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PucAccountUseCase useCase;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private UserDetailsService userDetailsService;

    @TestConfiguration
    static class JwtFilterTestConfig {
        @Bean
        JwtAuthFilter jwtAuthFilter(JwtService jwtService, UserDetailsService userDetailsService) {
            return new JwtAuthFilter(jwtService, userDetailsService);
        }
    }

    private static RequestPostProcessor asRole(String role) {
        var principal = new PosUserDetails(
                UUID.randomUUID(),
                "user-" + role.toLowerCase(),
                "irrelevant-hash",
                List.of(new SimpleGrantedAuthority("ROLE_" + role)),
                null
        );
        return authentication(new UsernamePasswordAuthenticationToken(
                principal, null, principal.getAuthorities()));
    }

    @Test
    @DisplayName("ADMIN can list PUC accounts (GET / -> 200)")
    void adminCanListAccounts() throws Exception {
        given(useCase.listAll()).willReturn(List.of());

        mockMvc.perform(get("/api/v1/puc-accounts").with(asRole("ADMIN")))
                .andExpect(status().isOk());

        then(useCase).should().listAll();
    }

    @Test
    @DisplayName("CONTADOR can list PUC accounts (GET / -> 200)")
    void contadorCanListAccounts() throws Exception {
        given(useCase.listAll()).willReturn(List.of());

        mockMvc.perform(get("/api/v1/puc-accounts").with(asRole("CONTADOR")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("CAJERO cannot list PUC accounts (GET / -> 403)")
    void cajeroCannotListAccounts() throws Exception {
        mockMvc.perform(get("/api/v1/puc-accounts").with(asRole("CAJERO")))
                .andExpect(status().isForbidden());

        then(useCase).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("CARNICERO cannot read a PUC account by id (GET /{id} -> 403)")
    void carniceroCannotReadAccountById() throws Exception {
        mockMvc.perform(get("/api/v1/puc-accounts/{id}", ACCOUNT_ID).with(asRole("CARNICERO")))
                .andExpect(status().isForbidden());

        then(useCase).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("CAJERO cannot read the PUC tree (GET /tree -> 403)")
    void cajeroCannotReadTree() throws Exception {
        mockMvc.perform(get("/api/v1/puc-accounts/tree").with(asRole("CAJERO")))
                .andExpect(status().isForbidden());

        then(useCase).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("CAJERO cannot check a PUC code (GET /check-code -> 403)")
    void cajeroCannotCheckCode() throws Exception {
        mockMvc.perform(get("/api/v1/puc-accounts/check-code").param("code", "1305")
                        .with(asRole("CAJERO")))
                .andExpect(status().isForbidden());

        then(useCase).shouldHaveNoInteractions();
    }
}
