package co.posinvent.integration;

import co.posinvent.application.usecase.SupplierBalanceUseCase;
import co.posinvent.infrastructure.adapters.in.rest.SupplierBalanceController;
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

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.then;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice tests for the supplier balance read endpoint role matrix (S3a).
 *
 * <p>The balance is a read model: {@code ADMIN}, {@code AUXILIAR} and {@code CONTADOR} may read it;
 * every other role gets 403 and the use case is never reached.</p>
 */
@WebMvcTest(controllers = SupplierBalanceController.class)
@ActiveProfiles("test")
@Import({SecurityConfig.class, SupplierBalanceControllerSecurityTest.JwtFilterTestConfig.class})
class SupplierBalanceControllerSecurityTest {

    private static final UUID SUPPLIER_ID = UUID.fromString("99999999-9999-9999-9999-999999999999");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SupplierBalanceUseCase supplierBalanceUseCase;

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
    @DisplayName("ADMIN can read the supplier balance (GET /{id}/balance -> 200)")
    void adminCanReadBalance() throws Exception {
        mockMvc.perform(get("/api/v1/suppliers/{id}/balance", SUPPLIER_ID).with(asRole("ADMIN")))
                .andExpect(status().isOk());

        then(supplierBalanceUseCase).should().getBalance(any());
    }

    @Test
    @DisplayName("AUXILIAR can read the supplier balance (GET /{id}/balance -> 200)")
    void auxiliarCanReadBalance() throws Exception {
        mockMvc.perform(get("/api/v1/suppliers/{id}/balance", SUPPLIER_ID).with(asRole("AUXILIAR")))
                .andExpect(status().isOk());

        then(supplierBalanceUseCase).should().getBalance(any());
    }

    @Test
    @DisplayName("CONTADOR can read the supplier balance (GET /{id}/balance -> 200)")
    void contadorCanReadBalance() throws Exception {
        mockMvc.perform(get("/api/v1/suppliers/{id}/balance", SUPPLIER_ID).with(asRole("CONTADOR")))
                .andExpect(status().isOk());

        then(supplierBalanceUseCase).should().getBalance(any());
    }

    @ParameterizedTest(name = "{0} cannot read the supplier balance (403, no interactions)")
    @ValueSource(strings = {"CAJERO", "VENDEDOR"})
    @DisplayName("other roles cannot read the supplier balance")
    void otherRolesCannotReadBalance(String role) throws Exception {
        mockMvc.perform(get("/api/v1/suppliers/{id}/balance", SUPPLIER_ID).with(asRole(role)))
                .andExpect(status().isForbidden());

        then(supplierBalanceUseCase).shouldHaveNoInteractions();
    }
}
