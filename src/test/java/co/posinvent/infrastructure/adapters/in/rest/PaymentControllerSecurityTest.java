package co.posinvent.infrastructure.adapters.in.rest;

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
import static org.mockito.BDDMockito.then;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice tests for the supplier-payment role matrix (M8).
 *
 * <p>Payments are readable by {@code ADMIN}, {@code AUXILIAR} and {@code CONTADOR}; the financial
 * write (create) stays restricted to {@code ADMIN} and {@code CONTADOR}. An {@code AUXILIAR} write
 * gets 403 and the use case is never reached.</p>
 */
@WebMvcTest(controllers = PaymentController.class)
@ActiveProfiles("test")
@Import({SecurityConfig.class, PaymentControllerSecurityTest.JwtFilterTestConfig.class})
class PaymentControllerSecurityTest {

    private static final UUID PAYMENT_ID = UUID.fromString("99999999-9999-9999-9999-999999999999");

    private static final String CREATE_BODY = """
            {
              "supplierId": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
              "amount": 100.00,
              "paymentDate": "2026-01-15",
              "method": "TRANSFER",
              "invoicePayments": [
                {
                  "invoiceId": "55555555-5555-5555-5555-555555555555",
                  "appliedAmount": 100.00
                }
              ]
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PaymentUseCase useCase;

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
    @DisplayName("AUXILIAR can list payments (GET / -> 200)")
    void auxiliarCanListPayments() throws Exception {
        mockMvc.perform(get("/api/v1/payments").with(asRole("AUXILIAR")))
                .andExpect(status().isOk());

        then(useCase).should().list(any());
    }

    @Test
    @DisplayName("AUXILIAR can read a payment by id (GET /{id} -> 200)")
    void auxiliarCanReadPaymentById() throws Exception {
        mockMvc.perform(get("/api/v1/payments/{id}", PAYMENT_ID).with(asRole("AUXILIAR")))
                .andExpect(status().isOk());

        then(useCase).should().getById(PAYMENT_ID);
    }

    @Test
    @DisplayName("CONTADOR can create a payment (POST / -> 201)")
    void contadorCanCreatePayment() throws Exception {
        mockMvc.perform(post("/api/v1/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_BODY)
                        .with(asRole("CONTADOR")))
                .andExpect(status().isCreated());

        then(useCase).should().create(any(), any());
    }

    @Test
    @DisplayName("AUXILIAR cannot create a payment (POST / -> 403)")
    void auxiliarCannotCreatePayment() throws Exception {
        mockMvc.perform(post("/api/v1/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_BODY)
                        .with(asRole("AUXILIAR")))
                .andExpect(status().isForbidden());

        then(useCase).shouldHaveNoInteractions();
    }
}
