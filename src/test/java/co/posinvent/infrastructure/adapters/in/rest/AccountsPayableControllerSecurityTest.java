package co.posinvent.infrastructure.adapters.in.rest;

import co.posinvent.application.usecase.AccountsPayableUseCase;
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
import org.springframework.data.domain.Page;
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice tests for the accounts-payable role matrix (M8).
 *
 * <p>The CxP reads (list/get/aging) are open to {@code ADMIN}, {@code AUXILIAR} and
 * {@code CONTADOR}. {@code mark-overdue} is intentionally NOT part of the financial-write matrix
 * and stays {@code ADMIN}-only.</p>
 */
@WebMvcTest(controllers = AccountsPayableController.class)
@ActiveProfiles("test")
@Import({SecurityConfig.class, AccountsPayableControllerSecurityTest.JwtFilterTestConfig.class})
class AccountsPayableControllerSecurityTest {

    private static final UUID AP_ID = UUID.fromString("12121212-1212-1212-1212-121212121212");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AccountsPayableUseCase useCase;

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
                null
        );
        return authentication(new UsernamePasswordAuthenticationToken(
                principal, null, principal.getAuthorities()));
    }

    @Test
    @DisplayName("AUXILIAR can list accounts payable (GET / -> 200)")
    void auxiliarCanListPayables() throws Exception {
        given(useCase.list(anyInt(), anyInt(), any(), any())).willReturn(Page.empty());

        mockMvc.perform(get("/api/v1/accounts-payable").with(asRole("AUXILIAR")))
                .andExpect(status().isOk());

        then(useCase).should().list(anyInt(), anyInt(), any(), any());
    }

    @Test
    @DisplayName("AUXILIAR can read an account payable by id (GET /{id} -> 200)")
    void auxiliarCanReadPayableById() throws Exception {
        mockMvc.perform(get("/api/v1/accounts-payable/{id}", AP_ID).with(asRole("AUXILIAR")))
                .andExpect(status().isOk());

        then(useCase).should().getById(AP_ID);
    }

    @Test
    @DisplayName("AUXILIAR can read the aging report (GET /aging -> 200)")
    void auxiliarCanReadAging() throws Exception {
        mockMvc.perform(get("/api/v1/accounts-payable/aging").with(asRole("AUXILIAR")))
                .andExpect(status().isOk());

        then(useCase).should().getAging(any());
    }

    @Test
    @DisplayName("ADMIN can mark accounts payable overdue (POST /mark-overdue -> 200)")
    void adminCanMarkOverdue() throws Exception {
        given(useCase.markOverdue()).willReturn(0);

        mockMvc.perform(post("/api/v1/accounts-payable/mark-overdue").with(asRole("ADMIN")))
                .andExpect(status().isOk());

        then(useCase).should().markOverdue();
    }

    @Test
    @DisplayName("AUXILIAR cannot mark accounts payable overdue (POST /mark-overdue -> 403)")
    void auxiliarCannotMarkOverdue() throws Exception {
        mockMvc.perform(post("/api/v1/accounts-payable/mark-overdue").with(asRole("AUXILIAR")))
                .andExpect(status().isForbidden());

        then(useCase).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("CONTADOR cannot mark accounts payable overdue (POST /mark-overdue -> 403)")
    void contadorCannotMarkOverdue() throws Exception {
        mockMvc.perform(post("/api/v1/accounts-payable/mark-overdue").with(asRole("CONTADOR")))
                .andExpect(status().isForbidden());

        then(useCase).shouldHaveNoInteractions();
    }
}
