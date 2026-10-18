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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice tests for the scale role matrix. Cashiers and warehouse/meat roles may poll the scale;
 * accounting may not.
 */
@WebMvcTest(controllers = ScaleController.class)
@ActiveProfiles("test")
@Import({SecurityConfig.class, ScaleControllerSecurityTest.JwtFilterTestConfig.class})
class ScaleControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

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
    @DisplayName("ADMIN can read the scale status (GET /status -> 200)")
    void adminCanReadScaleStatus() throws Exception {
        mockMvc.perform(get("/api/v1/scale/status").with(asRole("ADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("CAJERO can read the scale status (GET /status -> 200)")
    void cajeroCanReadScaleStatus() throws Exception {
        mockMvc.perform(get("/api/v1/scale/status").with(asRole("CAJERO")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("CARNICERO can read the scale status (GET /status -> 200)")
    void carniceroCanReadScaleStatus() throws Exception {
        mockMvc.perform(get("/api/v1/scale/status").with(asRole("CARNICERO")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("ALMACENISTA can read the scale status (GET /status -> 200)")
    void almacenistaCanReadScaleStatus() throws Exception {
        mockMvc.perform(get("/api/v1/scale/status").with(asRole("ALMACENISTA")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("AUXILIAR can read the scale status (GET /status -> 200)")
    void auxiliarCanReadScaleStatus() throws Exception {
        mockMvc.perform(get("/api/v1/scale/status").with(asRole("AUXILIAR")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("CONTADOR cannot read the scale status (GET /status -> 403)")
    void contadorCannotReadScaleStatus() throws Exception {
        mockMvc.perform(get("/api/v1/scale/status").with(asRole("CONTADOR")))
                .andExpect(status().isForbidden());
    }
}
