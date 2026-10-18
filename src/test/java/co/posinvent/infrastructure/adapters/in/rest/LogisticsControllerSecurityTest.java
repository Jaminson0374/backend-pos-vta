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
    private static final UUID PICKING_ID = UUID.fromString("88888888-8888-8888-8888-888888888888");
    private static final UUID SHIPMENT_ID = UUID.fromString("99999999-9999-9999-9999-999999999999");
    private static final UUID GUIDE_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

    private static final String PICKINGS_PATH = "/api/v1/logistics/pickings";
    private static final String SHIPMENTS_PATH = "/api/v1/logistics/shipments";
    private static final String TRANSPORT_GUIDES_PATH = "/api/v1/logistics/transport-guides";

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

    private void expectReadAllowed(String path, String role) throws Exception {
        mockMvc.perform(get(path).with(asRole(role)))
                .andExpect(status().isOk());
    }

    private void expectReadDenied(String path, String role) throws Exception {
        mockMvc.perform(get(path).with(asRole(role)))
                .andExpect(status().isForbidden());

        then(useCase).shouldHaveNoInteractions();
    }

    private void expectStatusUpdateAllowed(String path, UUID id, String status, String role) throws Exception {
        mockMvc.perform(patch(path, id)
                        .param("status", status)
                        .with(asRole(role)))
                .andExpect(status().isOk());
    }

    private void expectStatusUpdateDenied(String path, UUID id, String status, String role) throws Exception {
        mockMvc.perform(patch(path, id)
                        .param("status", status)
                        .with(asRole(role)))
                .andExpect(status().isForbidden());

        then(useCase).shouldHaveNoInteractions();
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

    // ── Pickings ───────────────────────────────────────────────────

    @Test
    @DisplayName("ADMIN can list pickings (GET /pickings -> 200)")
    void adminCanListPickings() throws Exception {
        expectReadAllowed(PICKINGS_PATH, "ADMIN");

        then(useCase).should().listPickings(any());
    }

    @Test
    @DisplayName("ALMACENISTA can list pickings (GET /pickings -> 200)")
    void almacenistaCanListPickings() throws Exception {
        expectReadAllowed(PICKINGS_PATH, "ALMACENISTA");

        then(useCase).should().listPickings(any());
    }

    @Test
    @DisplayName("AUXILIAR can list pickings (GET /pickings -> 200)")
    void auxiliarCanListPickings() throws Exception {
        expectReadAllowed(PICKINGS_PATH, "AUXILIAR");

        then(useCase).should().listPickings(any());
    }

    @Test
    @DisplayName("CONTADOR can list pickings (GET /pickings -> 200)")
    void contadorCanListPickings() throws Exception {
        expectReadAllowed(PICKINGS_PATH, "CONTADOR");

        then(useCase).should().listPickings(any());
    }

    @Test
    @DisplayName("CAJERO cannot list pickings (GET /pickings -> 403)")
    void cajeroCannotListPickings() throws Exception {
        expectReadDenied(PICKINGS_PATH, "CAJERO");
    }

    @Test
    @DisplayName("ADMIN can change a picking status (PATCH /pickings/{id}/status -> 200)")
    void adminCanUpdatePickingStatus() throws Exception {
        expectStatusUpdateAllowed(PICKINGS_PATH + "/{id}/status", PICKING_ID, "PICKED", "ADMIN");

        then(useCase).should().updatePickingStatus(any(), any());
    }

    @Test
    @DisplayName("ALMACENISTA can change a picking status (PATCH /pickings/{id}/status -> 200)")
    void almacenistaCanUpdatePickingStatus() throws Exception {
        expectStatusUpdateAllowed(PICKINGS_PATH + "/{id}/status", PICKING_ID, "PICKED", "ALMACENISTA");

        then(useCase).should().updatePickingStatus(any(), any());
    }

    @Test
    @DisplayName("AUXILIAR cannot change a picking status (PATCH /pickings/{id}/status -> 403)")
    void auxiliarCannotUpdatePickingStatus() throws Exception {
        expectStatusUpdateDenied(PICKINGS_PATH + "/{id}/status", PICKING_ID, "PICKED", "AUXILIAR");
    }

    // ── Shipments ──────────────────────────────────────────────────

    @Test
    @DisplayName("ADMIN can list shipments (GET /shipments -> 200)")
    void adminCanListShipments() throws Exception {
        expectReadAllowed(SHIPMENTS_PATH, "ADMIN");

        then(useCase).should().listShipments(any());
    }

    @Test
    @DisplayName("ALMACENISTA can list shipments (GET /shipments -> 200)")
    void almacenistaCanListShipments() throws Exception {
        expectReadAllowed(SHIPMENTS_PATH, "ALMACENISTA");

        then(useCase).should().listShipments(any());
    }

    @Test
    @DisplayName("AUXILIAR can list shipments (GET /shipments -> 200)")
    void auxiliarCanListShipments() throws Exception {
        expectReadAllowed(SHIPMENTS_PATH, "AUXILIAR");

        then(useCase).should().listShipments(any());
    }

    @Test
    @DisplayName("CONTADOR can list shipments (GET /shipments -> 200)")
    void contadorCanListShipments() throws Exception {
        expectReadAllowed(SHIPMENTS_PATH, "CONTADOR");

        then(useCase).should().listShipments(any());
    }

    @Test
    @DisplayName("CAJERO cannot list shipments (GET /shipments -> 403)")
    void cajeroCannotListShipments() throws Exception {
        expectReadDenied(SHIPMENTS_PATH, "CAJERO");
    }

    @Test
    @DisplayName("ADMIN can change a shipment status (PATCH /shipments/{id}/status -> 200)")
    void adminCanUpdateShipmentStatus() throws Exception {
        expectStatusUpdateAllowed(SHIPMENTS_PATH + "/{id}/status", SHIPMENT_ID, "SHIPPED", "ADMIN");

        then(useCase).should().updateShipmentStatus(any(), any());
    }

    @Test
    @DisplayName("ALMACENISTA can change a shipment status (PATCH /shipments/{id}/status -> 200)")
    void almacenistaCanUpdateShipmentStatus() throws Exception {
        expectStatusUpdateAllowed(SHIPMENTS_PATH + "/{id}/status", SHIPMENT_ID, "SHIPPED", "ALMACENISTA");

        then(useCase).should().updateShipmentStatus(any(), any());
    }

    @Test
    @DisplayName("AUXILIAR cannot change a shipment status (PATCH /shipments/{id}/status -> 403)")
    void auxiliarCannotUpdateShipmentStatus() throws Exception {
        expectStatusUpdateDenied(SHIPMENTS_PATH + "/{id}/status", SHIPMENT_ID, "SHIPPED", "AUXILIAR");
    }

    // ── Transport guides ───────────────────────────────────────────

    @Test
    @DisplayName("ADMIN can list transport guides (GET /transport-guides -> 200)")
    void adminCanListTransportGuides() throws Exception {
        expectReadAllowed(TRANSPORT_GUIDES_PATH, "ADMIN");

        then(useCase).should().listGuides(any());
    }

    @Test
    @DisplayName("ALMACENISTA can list transport guides (GET /transport-guides -> 200)")
    void almacenistaCanListTransportGuides() throws Exception {
        expectReadAllowed(TRANSPORT_GUIDES_PATH, "ALMACENISTA");

        then(useCase).should().listGuides(any());
    }

    @Test
    @DisplayName("AUXILIAR can list transport guides (GET /transport-guides -> 200)")
    void auxiliarCanListTransportGuides() throws Exception {
        expectReadAllowed(TRANSPORT_GUIDES_PATH, "AUXILIAR");

        then(useCase).should().listGuides(any());
    }

    @Test
    @DisplayName("CONTADOR can list transport guides (GET /transport-guides -> 200)")
    void contadorCanListTransportGuides() throws Exception {
        expectReadAllowed(TRANSPORT_GUIDES_PATH, "CONTADOR");

        then(useCase).should().listGuides(any());
    }

    @Test
    @DisplayName("CAJERO cannot list transport guides (GET /transport-guides -> 403)")
    void cajeroCannotListTransportGuides() throws Exception {
        expectReadDenied(TRANSPORT_GUIDES_PATH, "CAJERO");
    }

    @Test
    @DisplayName("ADMIN can change a transport guide status (PATCH /transport-guides/{id}/status -> 200)")
    void adminCanUpdateTransportGuideStatus() throws Exception {
        expectStatusUpdateAllowed(TRANSPORT_GUIDES_PATH + "/{id}/status", GUIDE_ID, "ISSUED", "ADMIN");

        then(useCase).should().updateGuideStatus(any(), any());
    }

    @Test
    @DisplayName("ALMACENISTA can change a transport guide status (PATCH /transport-guides/{id}/status -> 200)")
    void almacenistaCanUpdateTransportGuideStatus() throws Exception {
        expectStatusUpdateAllowed(TRANSPORT_GUIDES_PATH + "/{id}/status", GUIDE_ID, "ISSUED", "ALMACENISTA");

        then(useCase).should().updateGuideStatus(any(), any());
    }

    @Test
    @DisplayName("AUXILIAR cannot change a transport guide status (PATCH /transport-guides/{id}/status -> 403)")
    void auxiliarCannotUpdateTransportGuideStatus() throws Exception {
        expectStatusUpdateDenied(TRANSPORT_GUIDES_PATH + "/{id}/status", GUIDE_ID, "ISSUED", "AUXILIAR");
    }
}
