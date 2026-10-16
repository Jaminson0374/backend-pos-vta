package co.posinvent.infrastructure.adapters.in.rest;

import co.posinvent.application.dto.ElectronicInvoiceResponse;
import co.posinvent.application.usecase.ManageElectronicInvoiceUseCase;
import co.posinvent.application.usecase.PosCheckoutUseCase;
import co.posinvent.application.usecase.PosDevolutionUseCase;
import co.posinvent.domain.exception.ResourceNotFoundException;
import co.posinvent.domain.model.ElectronicInvoiceStatus;
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

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice tests for the POS DIAN-status endpoint
 * ({@code GET /api/v1/pos/invoices/{salesDocumentId}/dian}). {@code ADMIN} and {@code CAJERO}
 * must read the Siigo/DIAN status and CUFE, {@code VENDEDOR} must get 403, and a missing
 * electronic invoice must surface as 404 so the cashier can poll cleanly.
 */
@WebMvcTest(controllers = PosController.class)
@ActiveProfiles("test")
@Import({SecurityConfig.class, PosControllerDianStatusTest.JwtFilterTestConfig.class})
class PosControllerDianStatusTest {

    private static final UUID SALES_DOCUMENT_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

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
    private ManageElectronicInvoiceUseCase electronicInvoiceUseCase;

    @MockitoBean
    private co.posinvent.application.usecase.AuthorizeDiscountUseCase authorizeDiscountUseCase;

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

    private static ElectronicInvoiceResponse response() {
        return new ElectronicInvoiceResponse(
                UUID.randomUUID(),
                SALES_DOCUMENT_ID,
                null,
                "CUFE-ABC-123",
                "qr-payload",
                ElectronicInvoiceStatus.PENDING_SEND,
                null,
                null,
                OffsetDateTime.now());
    }

    @Test
    @DisplayName("CAJERO can read the DIAN status (GET /invoices/{id}/dian -> 200)")
    void cajeroCanReadDianStatus() throws Exception {
        given(electronicInvoiceUseCase.getBySalesDocument(SALES_DOCUMENT_ID)).willReturn(response());

        mockMvc.perform(get("/api/v1/pos/invoices/{id}/dian", SALES_DOCUMENT_ID)
                        .with(asRole("CAJERO")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_SEND"))
                .andExpect(jsonPath("$.cufe").value("CUFE-ABC-123"))
                .andExpect(jsonPath("$.qrCode").value("qr-payload"))
                .andExpect(jsonPath("$.salesDocumentId").value(SALES_DOCUMENT_ID.toString()));

        then(electronicInvoiceUseCase).should().getBySalesDocument(eq(SALES_DOCUMENT_ID));
    }

    @Test
    @DisplayName("ADMIN can read the DIAN status (GET /invoices/{id}/dian -> 200)")
    void adminCanReadDianStatus() throws Exception {
        given(electronicInvoiceUseCase.getBySalesDocument(SALES_DOCUMENT_ID)).willReturn(response());

        mockMvc.perform(get("/api/v1/pos/invoices/{id}/dian", SALES_DOCUMENT_ID)
                        .with(asRole("ADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Missing electronic invoice surfaces as 404 so the POS can poll cleanly")
    void missingElectronicInvoiceReturns404() throws Exception {
        given(electronicInvoiceUseCase.getBySalesDocument(SALES_DOCUMENT_ID))
                .willThrow(new ResourceNotFoundException("Factura electrónica", SALES_DOCUMENT_ID));

        mockMvc.perform(get("/api/v1/pos/invoices/{id}/dian", SALES_DOCUMENT_ID)
                        .with(asRole("CAJERO")))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("VENDEDOR cannot read the DIAN status (GET /invoices/{id}/dian -> 403)")
    void vendedorCannotReadDianStatus() throws Exception {
        mockMvc.perform(get("/api/v1/pos/invoices/{id}/dian", SALES_DOCUMENT_ID)
                        .with(asRole("VENDEDOR")))
                .andExpect(status().isForbidden());

        then(electronicInvoiceUseCase).shouldHaveNoInteractions();
    }
}
