package co.posinvent.infrastructure.adapters.in.rest;

import co.posinvent.application.usecase.LogisticsUseCase;
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
 * Slice tests for the logistics role matrix. Reads stay open to {@code ADMIN},
 * {@code ALMACENISTA}, {@code AUXILIAR} and {@code CONTADOR}; writes are limited to
 * {@code ADMIN} and {@code ALMACENISTA}, so {@code AUXILIAR} loses write access on purpose.
 */
@WebMvcTest(controllers = LogisticsController.class)
@ActiveProfiles("test")
@Import({SecurityConfig.class, LogisticsControllerSecurityTest.JwtFilterTestConfig.class})
class LogisticsControllerSecurityTest {

    private static final UUID RECEIPT_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");
    private static final UUID WAREHOUSE_ID = UUID.fromString("66666666-6666-6666-6666-666666666666");
    private static final UUID PRODUCT_ID = UUID.fromString("77777777-7777-7777-7777-777777777777");

    private static final String CREATE_RECEIPT_BODY = """
            {
              "receiptNumber": "R-001",
              "receiptDate": "2026-01-15",
              "warehouseId": "%s",
              "items": [
                {
                  "productId": "%s",
                  "warehouseId": "%s",
                  "receivedQuantity": 1,
                  "unitCost": 10.00
                }
              ]
            }
            """.formatted(WAREHOUSE_ID, PRODUCT_ID, WAREHOUSE_ID);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LogisticsUseCase useCase;

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

    private void expectReceiptReadAllowed(String role) throws Exception {
        mockMvc.perform(get("/api/v1/logistics/receipts").with(asRole(role)))
                .andExpect(status().isOk());

        then(useCase).should().listReceipts(any());
    }

    @Test
    @DisplayName("ADMIN can list receipts (GET /receipts -> 200)")
    void adminCanListReceipts() throws Exception {
        expectReceiptReadAllowed("ADMIN");
    }

    @Test
    @DisplayName("ALMACENISTA can list receipts (GET /receipts -> 200)")
    void almacenistaCanListReceipts() throws Exception {
        expectReceiptReadAllowed("ALMACENISTA");
    }

    @Test
    @DisplayName("AUXILIAR can list receipts (GET /receipts -> 200)")
    void auxiliarCanListReceipts() throws Exception {
        expectReceiptReadAllowed("AUXILIAR");
    }

    @Test
    @DisplayName("CONTADOR can list receipts (GET /receipts -> 200)")
    void contadorCanListReceipts() throws Exception {
        expectReceiptReadAllowed("CONTADOR");
    }

    @Test
    @DisplayName("CAJERO cannot list receipts (GET /receipts -> 403)")
    void cajeroCannotListReceipts() throws Exception {
        mockMvc.perform(get("/api/v1/logistics/receipts").with(asRole("CAJERO")))
                .andExpect(status().isForbidden());

        then(useCase).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("ADMIN can create a receipt (POST /receipts -> 201)")
    void adminCanCreateReceipt() throws Exception {
        mockMvc.perform(post("/api/v1/logistics/receipts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_RECEIPT_BODY)
                        .with(asRole("ADMIN")))
                .andExpect(status().isCreated());

        then(useCase).should().createReceipt(any());
    }

    @Test
    @DisplayName("ALMACENISTA can create a receipt (POST /receipts -> 201)")
    void almacenistaCanCreateReceipt() throws Exception {
        mockMvc.perform(post("/api/v1/logistics/receipts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_RECEIPT_BODY)
                        .with(asRole("ALMACENISTA")))
                .andExpect(status().isCreated());

        then(useCase).should().createReceipt(any());
    }

    @Test
    @DisplayName("AUXILIAR cannot create a receipt (POST /receipts -> 403)")
    void auxiliarCannotCreateReceipt() throws Exception {
        mockMvc.perform(post("/api/v1/logistics/receipts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_RECEIPT_BODY)
                        .with(asRole("AUXILIAR")))
                .andExpect(status().isForbidden());

        then(useCase).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("ADMIN can change a receipt status (PATCH /receipts/{id}/status -> 200)")
    void adminCanUpdateReceiptStatus() throws Exception {
        mockMvc.perform(patch("/api/v1/logistics/receipts/{id}/status", RECEIPT_ID)
                        .param("status", "RECEIVED")
                        .with(asRole("ADMIN")))
                .andExpect(status().isOk());

        then(useCase).should().updateReceiptStatus(any(), any());
    }

    @Test
    @DisplayName("AUXILIAR cannot change a receipt status (PATCH /receipts/{id}/status -> 403)")
    void auxiliarCannotUpdateReceiptStatus() throws Exception {
        mockMvc.perform(patch("/api/v1/logistics/receipts/{id}/status", RECEIPT_ID)
                        .param("status", "RECEIVED")
                        .with(asRole("AUXILIAR")))
                .andExpect(status().isForbidden());

        then(useCase).shouldHaveNoInteractions();
    }
}
