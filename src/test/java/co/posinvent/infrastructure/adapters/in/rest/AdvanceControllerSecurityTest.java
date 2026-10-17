package co.posinvent.infrastructure.adapters.in.rest;

import co.posinvent.application.usecase.ApplyAdvanceUseCase;
import co.posinvent.application.usecase.PaymentUseCase;
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
import org.springframework.http.MediaType;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.then;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice tests for the advance-payment role matrix (M8).
 *
 * <p>The advance list is readable by {@code ADMIN}, {@code AUXILIAR} and {@code CONTADOR}; the
 * financial writes (create/apply) are restricted to {@code ADMIN} and {@code CONTADOR}. An
 * {@code AUXILIAR} write gets 403 and the use case is never reached.</p>
 */
@WebMvcTest(controllers = AdvanceController.class)
@ActiveProfiles("test")
@Import({SecurityConfig.class, AdvanceControllerSecurityTest.JwtFilterTestConfig.class})
class AdvanceControllerSecurityTest {

    private static final UUID ADVANCE_ID = UUID.fromString("77777777-7777-7777-7777-777777777777");
    private static final UUID INVOICE_ID = UUID.fromString("88888888-8888-8888-8888-888888888888");

    private static final String CREATE_BODY = """
            {
              "supplierId": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
              "amount": 100.00,
              "paymentDate": "2026-01-15",
              "method": "TRANSFER"
            }
            """;

    private static final String APPLY_BODY = """
            {
              "advancePaymentId": "77777777-7777-7777-7777-777777777777",
              "invoiceId": "88888888-8888-8888-8888-888888888888",
              "appliedAmount": 40.00
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PaymentUseCase paymentUseCase;

    @MockitoBean
    private ApplyAdvanceUseCase applyAdvanceUseCase;

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
    @DisplayName("AUXILIAR can list advances (GET / -> 200)")
    void auxiliarCanListAdvances() throws Exception {
        mockMvc.perform(get("/api/v1/payments/advances").with(asRole("AUXILIAR")))
                .andExpect(status().isOk());

        then(paymentUseCase).should().listAdvances(any(), any());
    }

    @Test
    @DisplayName("CONTADOR can create an advance (POST / -> 201)")
    void contadorCanCreateAdvance() throws Exception {
        mockMvc.perform(post("/api/v1/payments/advances")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_BODY)
                        .with(asRole("CONTADOR")))
                .andExpect(status().isCreated());

        then(paymentUseCase).should().createAdvance(any(), any());
    }

    @Test
    @DisplayName("AUXILIAR cannot create an advance (POST / -> 403)")
    void auxiliarCannotCreateAdvance() throws Exception {
        mockMvc.perform(post("/api/v1/payments/advances")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_BODY)
                        .with(asRole("AUXILIAR")))
                .andExpect(status().isForbidden());

        then(paymentUseCase).shouldHaveNoInteractions();
        then(applyAdvanceUseCase).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("CONTADOR can apply an advance (POST /{id}/apply -> 200)")
    void contadorCanApplyAdvance() throws Exception {
        mockMvc.perform(post("/api/v1/payments/advances/{id}/apply", ADVANCE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(APPLY_BODY)
                        .with(asRole("CONTADOR")))
                .andExpect(status().isOk());

        then(applyAdvanceUseCase).should().apply(any(), any());
    }

    @Test
    @DisplayName("AUXILIAR cannot apply an advance (POST /{id}/apply -> 403)")
    void auxiliarCannotApplyAdvance() throws Exception {
        mockMvc.perform(post("/api/v1/payments/advances/{id}/apply", ADVANCE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(APPLY_BODY)
                        .with(asRole("AUXILIAR")))
                .andExpect(status().isForbidden());

        then(paymentUseCase).shouldHaveNoInteractions();
        then(applyAdvanceUseCase).shouldHaveNoInteractions();
    }
}
