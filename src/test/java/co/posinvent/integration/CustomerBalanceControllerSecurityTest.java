package co.posinvent.integration;

import co.posinvent.application.dto.CustomerBalanceResponse;
import co.posinvent.application.usecase.CustomerBalanceUseCase;
import co.posinvent.domain.exception.ResourceNotFoundException;
import co.posinvent.infrastructure.adapters.in.rest.CustomerBalanceController;
import co.posinvent.infrastructure.adapters.out.security.PosUserDetails;
import co.posinvent.infrastructure.config.JwtAuthFilter;
import co.posinvent.infrastructure.config.JwtService;
import co.posinvent.infrastructure.config.SecurityConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice tests for the derived customer balance read endpoint role matrix (A1).
 *
 * <p>The balance is a read model: {@code ADMIN}, {@code AUXILIAR} and {@code CONTADOR} may read it;
 * every other role gets 403 and the use case is never reached.</p>
 */
@WebMvcTest(controllers = CustomerBalanceController.class)
@ActiveProfiles("test")
@Import({SecurityConfig.class, CustomerBalanceControllerSecurityTest.JwtFilterTestConfig.class})
class CustomerBalanceControllerSecurityTest {

    private static final UUID CLIENT_ID = UUID.fromString("88888888-8888-8888-8888-888888888888");
    private static final CustomerBalanceResponse BALANCE = new CustomerBalanceResponse(
            CLIENT_ID,
            new BigDecimal("600.00"),
            new BigDecimal("1000.00"),
            new BigDecimal("400.00"));

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CustomerBalanceUseCase customerBalanceUseCase;

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
    @DisplayName("ADMIN can read the customer balance (GET /{id}/balance -> 200)")
    void adminCanReadBalance() throws Exception {
        given(customerBalanceUseCase.getBalance(CLIENT_ID)).willReturn(BALANCE);

        mockMvc.perform(get("/api/v1/clients/{id}/balance", CLIENT_ID).with(asRole("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clientId").value(CLIENT_ID.toString()))
                .andExpect(jsonPath("$.outstanding").value(600.00))
                .andExpect(jsonPath("$.creditLimit").value(1000.00))
                .andExpect(jsonPath("$.availableCredit").value(400.00));

        then(customerBalanceUseCase).should().getBalance(any());
    }

    @Test
    @DisplayName("AUXILIAR can read the customer balance (GET /{id}/balance -> 200)")
    void auxiliarCanReadBalance() throws Exception {
        given(customerBalanceUseCase.getBalance(CLIENT_ID)).willReturn(BALANCE);

        mockMvc.perform(get("/api/v1/clients/{id}/balance", CLIENT_ID).with(asRole("AUXILIAR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clientId").value(CLIENT_ID.toString()))
                .andExpect(jsonPath("$.outstanding").value(600.00))
                .andExpect(jsonPath("$.creditLimit").value(1000.00))
                .andExpect(jsonPath("$.availableCredit").value(400.00));

        then(customerBalanceUseCase).should().getBalance(any());
    }

    @Test
    @DisplayName("CONTADOR can read the customer balance (GET /{id}/balance -> 200)")
    void contadorCanReadBalance() throws Exception {
        given(customerBalanceUseCase.getBalance(CLIENT_ID)).willReturn(BALANCE);

        mockMvc.perform(get("/api/v1/clients/{id}/balance", CLIENT_ID).with(asRole("CONTADOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clientId").value(CLIENT_ID.toString()))
                .andExpect(jsonPath("$.outstanding").value(600.00))
                .andExpect(jsonPath("$.creditLimit").value(1000.00))
                .andExpect(jsonPath("$.availableCredit").value(400.00));

        then(customerBalanceUseCase).should().getBalance(any());
    }

    @Test
    @DisplayName("unknown client maps the use case failure to 404 NOT_FOUND")
    void missingClientReturnsNotFound() throws Exception {
        given(customerBalanceUseCase.getBalance(CLIENT_ID))
                .willThrow(new ResourceNotFoundException("Cliente", CLIENT_ID));

        mockMvc.perform(get("/api/v1/clients/{id}/balance", CLIENT_ID).with(asRole("ADMIN")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("NOT_FOUND"));

        then(customerBalanceUseCase).should().getBalance(any());
    }

    @ParameterizedTest(name = "{0} cannot read the customer balance (403, no interactions)")
    @ValueSource(strings = {"CAJERO", "VENDEDOR"})
    @DisplayName("other roles cannot read the customer balance")
    void otherRolesCannotReadBalance(String role) throws Exception {
        mockMvc.perform(get("/api/v1/clients/{id}/balance", CLIENT_ID).with(asRole(role)))
                .andExpect(status().isForbidden());

        then(customerBalanceUseCase).shouldHaveNoInteractions();
    }
}
