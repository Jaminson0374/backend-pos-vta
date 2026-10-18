package co.posinvent.infrastructure.adapters.in.rest;

import co.posinvent.application.usecase.SupplierInvoiceUseCase;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice tests for the supplier-invoice role matrix (M8).
 *
 * <p>Reads stay open to {@code ADMIN}, {@code AUXILIAR} and {@code CONTADOR}; writes are financial
 * and restricted to {@code ADMIN} and {@code CONTADOR}. An {@code AUXILIAR} write gets 403 and the
 * use case is never reached.</p>
 */
@WebMvcTest(controllers = SupplierInvoiceController.class)
@ActiveProfiles("test")
@Import({SecurityConfig.class, SupplierInvoiceControllerSecurityTest.JwtFilterTestConfig.class})
class SupplierInvoiceControllerSecurityTest {

    private static final UUID INVOICE_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");

    private static final String CREATE_BODY = """
            {
              "supplierId": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
              "invoiceNumber": "F-001",
              "issueDate": "2026-01-15",
              "subtotal": 100.00,
              "ivaTotal": 19.00,
              "total": 119.00
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SupplierInvoiceUseCase useCase;

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
    @DisplayName("AUXILIAR cannot create a supplier invoice (POST / -> 403)")
    void auxiliarCannotCreateInvoice() throws Exception {
        mockMvc.perform(post("/api/v1/supplier-invoices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_BODY)
                        .with(asRole("AUXILIAR")))
                .andExpect(status().isForbidden());

        then(useCase).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("CONTADOR can create a supplier invoice (POST / -> 201)")
    void contadorCanCreateInvoice() throws Exception {
        mockMvc.perform(post("/api/v1/supplier-invoices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_BODY)
                        .with(asRole("CONTADOR")))
                .andExpect(status().isCreated());

        then(useCase).should().create(any(), any());
    }

    @Test
    @DisplayName("AUXILIAR cannot reconcile a supplier invoice (PATCH /{id}/reconcile -> 403)")
    void auxiliarCannotReconcileInvoice() throws Exception {
        mockMvc.perform(patch("/api/v1/supplier-invoices/{id}/reconcile", INVOICE_ID)
                        .with(asRole("AUXILIAR")))
                .andExpect(status().isForbidden());

        then(useCase).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("CONTADOR can reconcile a supplier invoice (PATCH /{id}/reconcile -> 200)")
    void contadorCanReconcileInvoice() throws Exception {
        mockMvc.perform(patch("/api/v1/supplier-invoices/{id}/reconcile", INVOICE_ID)
                        .with(asRole("CONTADOR")))
                .andExpect(status().isOk());

        then(useCase).should().reconcile(INVOICE_ID);
    }

    @Test
    @DisplayName("AUXILIAR cannot dispute a supplier invoice (PATCH /{id}/dispute -> 403)")
    void auxiliarCannotDisputeInvoice() throws Exception {
        mockMvc.perform(patch("/api/v1/supplier-invoices/{id}/dispute", INVOICE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"precio incorrecto\"}")
                        .with(asRole("AUXILIAR")))
                .andExpect(status().isForbidden());

        then(useCase).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("AUXILIAR can list supplier invoices (GET / -> 200)")
    void auxiliarCanListInvoices() throws Exception {
        mockMvc.perform(get("/api/v1/supplier-invoices").with(asRole("AUXILIAR")))
                .andExpect(status().isOk());

        then(useCase).should().list(any());
    }

    @Test
    @DisplayName("AUXILIAR can read a supplier invoice by id (GET /{id} -> 200)")
    void auxiliarCanReadInvoiceById() throws Exception {
        mockMvc.perform(get("/api/v1/supplier-invoices/{id}", INVOICE_ID)
                        .with(asRole("AUXILIAR")))
                .andExpect(status().isOk());

        then(useCase).should().getById(INVOICE_ID);
    }
}
