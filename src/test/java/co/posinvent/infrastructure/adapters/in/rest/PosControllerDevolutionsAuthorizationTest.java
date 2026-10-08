package co.posinvent.infrastructure.adapters.in.rest;

import co.posinvent.application.dto.DevolutionResponse;
import co.posinvent.application.usecase.PosCheckoutUseCase;
import co.posinvent.application.usecase.PosDevolutionUseCase;
import co.posinvent.domain.repository.ProductRepository;
import co.posinvent.domain.repository.SalesDocumentRepository;
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
import org.springframework.http.MediaType;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Regression tests for the R3-1/R3-2 findings: the {@code /devolutions} endpoints must be
 * restricted to {@code ADMIN}/{@code CAJERO}. {@code CAJERO} is allowed; {@code VENDEDOR} must
 * receive 403 before the use case is ever reached.
 */
@WebMvcTest(controllers = PosController.class)
@ActiveProfiles("test")
@Import({SecurityConfig.class, PosControllerDevolutionsAuthorizationTest.JwtFilterTestConfig.class})
class PosControllerDevolutionsAuthorizationTest {

    private static final UUID INVOICE_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID PRODUCT_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

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
    private JwtService jwtService;

    @MockitoBean
    private UserDetailsService userDetailsService;

    /**
     * Registers the real {@link JwtAuthFilter} with mocked collaborators so the security filter
     * chain from {@link SecurityConfig} can be wired without a running database.
     */
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

    private static String validBody() {
        return """
                {
                  "invoiceId": "%s",
                  "items": [ { "productId": "%s", "quantity": 1 } ],
                  "reason": "Producto defectuoso"
                }
                """.formatted(INVOICE_ID, PRODUCT_ID);
    }

    @Test
    @DisplayName("R3-1: CAJERO can process a devolution (POST /devolutions -> 201)")
    void cajeroCanProcessDevolution() throws Exception {
        given(devolutionUseCase.processDevolution(any(), any()))
                .willReturn(new DevolutionResponse(
                        UUID.randomUUID(), "NC-1", new BigDecimal("10.00"), 1, BigDecimal.ZERO));

        mockMvc.perform(post("/api/v1/pos/devolutions")
                        .with(asRole("CAJERO"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody()))
                .andExpect(status().isCreated());

        then(devolutionUseCase).should().processDevolution(any(), any());
    }

    @Test
    @DisplayName("R3-2: VENDEDOR cannot process a devolution (POST /devolutions -> 403)")
    void vendedorCannotProcessDevolution() throws Exception {
        mockMvc.perform(post("/api/v1/pos/devolutions")
                        .with(asRole("VENDEDOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody()))
                .andExpect(status().isForbidden());

        then(devolutionUseCase).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("R3-1: CAJERO can list devolutions (GET /devolutions -> 200)")
    void cajeroCanListDevolutions() throws Exception {
        given(salesDocumentRepository.findBySourceDocumentId(any())).willReturn(List.of());

        mockMvc.perform(get("/api/v1/pos/devolutions")
                        .param("invoiceId", INVOICE_ID.toString())
                        .with(asRole("CAJERO")))
                .andExpect(status().isOk());

        then(salesDocumentRepository).should().findBySourceDocumentId(any());
    }

    @Test
    @DisplayName("R3-2: VENDEDOR cannot list devolutions (GET /devolutions -> 403)")
    void vendedorCannotListDevolutions() throws Exception {
        mockMvc.perform(get("/api/v1/pos/devolutions")
                        .param("invoiceId", INVOICE_ID.toString())
                        .with(asRole("VENDEDOR")))
                .andExpect(status().isForbidden());

        then(salesDocumentRepository).shouldHaveNoInteractions();
    }
}
