package co.posinvent.infrastructure.adapters.in.rest;

import co.posinvent.application.dto.DiscountAuthorizationResponse;
import co.posinvent.application.usecase.AuthorizeDiscountUseCase;
import co.posinvent.application.usecase.ManageElectronicInvoiceUseCase;
import co.posinvent.application.usecase.PosCheckoutUseCase;
import co.posinvent.application.usecase.PosDevolutionUseCase;
import co.posinvent.domain.repository.ProductRepository;
import co.posinvent.domain.repository.SalesDocumentRepository;
import co.posinvent.infrastructure.adapters.out.security.PosUserDetails;
import co.posinvent.infrastructure.config.JwtAuthFilter;
import co.posinvent.application.service.JwtBlacklistService;
import co.posinvent.infrastructure.config.JwtService;
import co.posinvent.infrastructure.config.SecurityConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
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

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice tests for the discount authorization endpoint
 * ({@code POST /api/v1/pos/discount-authorizations}). {@code ADMIN} and {@code CAJERO}
 * may request a token; {@code VENDEDOR} must receive 403 before the use case runs.
 */
@WebMvcTest(controllers = PosController.class)
@ActiveProfiles("test")
@Import({SecurityConfig.class, DiscountAuthorizationEndpointTest.JwtFilterTestConfig.class})
class DiscountAuthorizationEndpointTest {

    private static final UUID CASH_REGISTER_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PosCheckoutUseCase checkoutUseCase;

    @MockitoBean
    private PosDevolutionUseCase devolutionUseCase;

    @MockitoBean
    private ProductRepository productRepository;

    @MockitoBean
    private SalesDocumentRepository salesDocumentRepository;

    @MockitoBean
    private ManageElectronicInvoiceUseCase manageElectronicInvoiceUseCase;

    @MockitoBean
    private AuthorizeDiscountUseCase authorizeDiscountUseCase;

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

    private static String validBody() {
        return """
                {
                  "username": "admin1",
                  "password": "secret",
                  "cashRegisterId": "%s"
                }
                """.formatted(CASH_REGISTER_ID);
    }

    private static DiscountAuthorizationResponse response() {
        return new DiscountAuthorizationResponse(
                UUID.randomUUID(), OffsetDateTime.now().plusMinutes(2), "admin1");
    }

    @Test
    @DisplayName("CAJERO can request a discount authorization token (POST -> 201)")
    void cajeroCanRequestAuthorization() throws Exception {
        given(authorizeDiscountUseCase.authorize(any(), any(), any(), any())).willReturn(response());

        mockMvc.perform(post("/api/v1/pos/discount-authorizations")
                        .with(asRole("CAJERO"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.authorizationId").exists())
                .andExpect(jsonPath("$.authorizedBy").value("admin1"));

        then(authorizeDiscountUseCase).should().authorize(any(), any(), any(), any());
    }

    @Test
    @DisplayName("ADMIN can request a discount authorization token (POST -> 201)")
    void adminCanRequestAuthorization() throws Exception {
        given(authorizeDiscountUseCase.authorize(any(), any(), any(), any())).willReturn(response());

        mockMvc.perform(post("/api/v1/pos/discount-authorizations")
                        .with(asRole("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody()))
                .andExpect(status().isCreated());

        then(authorizeDiscountUseCase).should().authorize(any(), any(), any(), any());
    }

    @Test
    @DisplayName("VENDEDOR cannot request a discount authorization token (POST -> 403)")
    void vendedorCannotRequestAuthorization() throws Exception {
        mockMvc.perform(post("/api/v1/pos/discount-authorizations")
                        .with(asRole("VENDEDOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody()))
                .andExpect(status().isForbidden());

        then(authorizeDiscountUseCase).shouldHaveNoInteractions();
    }
}
