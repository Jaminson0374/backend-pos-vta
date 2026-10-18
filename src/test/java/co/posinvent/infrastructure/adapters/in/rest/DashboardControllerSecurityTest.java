package co.posinvent.infrastructure.adapters.in.rest;

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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;
import java.util.UUID;

import static org.mockito.BDDMockito.then;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice tests for the operational dashboard role matrix. Only {@code ADMIN} and
 * {@code CONTADOR} may read it.
 */
@WebMvcTest(controllers = DashboardController.class)
@ActiveProfiles("test")
@Import({SecurityConfig.class, DashboardControllerSecurityTest.JwtFilterTestConfig.class})
class DashboardControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JdbcTemplate jdbcTemplate;

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

    @Test
    @DisplayName("ADMIN can read the dashboard summary (GET /summary -> 200)")
    void adminCanReadSummary() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard/summary").with(asRole("ADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("CONTADOR can read the dashboard summary (GET /summary -> 200)")
    void contadorCanReadSummary() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard/summary").with(asRole("CONTADOR")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("CAJERO cannot read the dashboard summary (GET /summary -> 403)")
    void cajeroCannotReadSummary() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard/summary").with(asRole("CAJERO")))
                .andExpect(status().isForbidden());

        then(jdbcTemplate).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("VENDEDOR cannot read the dashboard summary (GET /summary -> 403)")
    void vendedorCannotReadSummary() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard/summary").with(asRole("VENDEDOR")))
                .andExpect(status().isForbidden());

        then(jdbcTemplate).shouldHaveNoInteractions();
    }
}
