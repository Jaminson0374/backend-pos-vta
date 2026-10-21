package co.posinvent.infrastructure.adapters.in.rest;

import co.posinvent.application.dto.WarehouseAccessResponse;
import co.posinvent.application.service.JwtBlacklistService;
import co.posinvent.application.usecase.GetCurrentWarehouseAccessUseCase;
import co.posinvent.infrastructure.adapters.out.security.PosUserDetails;
import co.posinvent.infrastructure.config.JwtAuthFilter;
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
import java.util.Set;
import java.util.UUID;

import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = CurrentWarehouseAccessController.class)
@ActiveProfiles("test")
@Import({SecurityConfig.class, CurrentWarehouseAccessControllerTest.JwtFilterTestConfig.class})
class CurrentWarehouseAccessControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GetCurrentWarehouseAccessUseCase useCase;

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

    @Test
    @DisplayName("ADMIN can read its warehouse access (GET / -> 200)")
    void adminCanReadAccess() throws Exception {
        given(useCase.execute()).willReturn(new WarehouseAccessResponse(true, Set.of()));

        mockMvc.perform(get("/api/v1/me/warehouses").with(asRole("ADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("ALMACENISTA can read its warehouse access (GET / -> 200)")
    void almacenistaCanReadAccess() throws Exception {
        given(useCase.execute())
                .willReturn(new WarehouseAccessResponse(false, Set.of(UUID.randomUUID())));

        mockMvc.perform(get("/api/v1/me/warehouses").with(asRole("ALMACENISTA")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Anonymous cannot read warehouse access (GET / -> 401)")
    void anonymousCannotReadAccess() throws Exception {
        mockMvc.perform(get("/api/v1/me/warehouses"))
                .andExpect(status().isUnauthorized());
    }

    private static RequestPostProcessor asRole(String role) {
        var principal = new PosUserDetails(
                UUID.randomUUID(), "user-" + role.toLowerCase(), "irrelevant-hash",
                List.of(new SimpleGrantedAuthority("ROLE_" + role)), null, 0L);
        return authentication(new UsernamePasswordAuthenticationToken(
                principal, null, principal.getAuthorities()));
    }
}
