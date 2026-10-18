package co.posinvent.infrastructure.adapters.in.rest;

import co.posinvent.application.dto.PageResponse;
import co.posinvent.application.dto.PurchaseOrderResponse;
import co.posinvent.application.usecase.PurchaseOrderUseCase;
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
import org.springframework.data.domain.Pageable;
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
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice tests for the purchase-order role matrix (A7, M8).
 *
 * <p>Reads ({@code GET /api/v1/purchase-orders}, {@code GET /{id}}) are open to {@code ADMIN},
 * {@code AUXILIAR} and {@code CONTADOR}. Writes are operational and restricted to {@code ADMIN}
 * and {@code AUXILIAR}; a {@code CONTADOR} write gets 403 and the use case is never reached.</p>
 */
@WebMvcTest(controllers = PurchaseOrderController.class)
@ActiveProfiles("test")
@Import({SecurityConfig.class, PurchaseOrderControllerSecurityTest.JwtFilterTestConfig.class})
class PurchaseOrderControllerSecurityTest {

    private static final UUID ORDER_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");

    private static final String ORDER_BODY = """
            {
              "supplierId": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
              "orderDate": "2026-01-15",
              "lines": [
                {
                  "productId": "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb",
                  "orderedQty": 2.0,
                  "unitCost": 10.00,
                  "warehouseId": "cccccccc-cccc-cccc-cccc-cccccccccccc"
                }
              ]
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PurchaseOrderUseCase useCase;

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

    private static PageResponse<PurchaseOrderResponse> emptyPage() {
        return new PageResponse<>(List.of(), 0, 20, 0, 0, true);
    }

    @Test
    @DisplayName("AUXILIAR can list purchase orders (GET / -> 200)")
    void auxiliarCanListOrders() throws Exception {
        given(useCase.list(any(Pageable.class))).willReturn(emptyPage());

        mockMvc.perform(get("/api/v1/purchase-orders").with(asRole("AUXILIAR")))
                .andExpect(status().isOk());

        then(useCase).should().list(any(Pageable.class));
    }

    @Test
    @DisplayName("ADMIN can list purchase orders (GET / -> 200)")
    void adminCanListOrders() throws Exception {
        given(useCase.list(any(Pageable.class))).willReturn(emptyPage());

        mockMvc.perform(get("/api/v1/purchase-orders").with(asRole("ADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("CAJERO cannot list purchase orders (GET / -> 403)")
    void cajeroCannotListOrders() throws Exception {
        mockMvc.perform(get("/api/v1/purchase-orders").with(asRole("CAJERO")))
                .andExpect(status().isForbidden());

        then(useCase).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("CARNICERO cannot read a purchase order by id (GET /{id} -> 403)")
    void carniceroCannotReadOrderById() throws Exception {
        mockMvc.perform(get("/api/v1/purchase-orders/{id}", ORDER_ID).with(asRole("CARNICERO")))
                .andExpect(status().isForbidden());

        then(useCase).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("AUXILIAR can read a purchase order by id (GET /{id} -> 200)")
    void auxiliarCanReadOrderById() throws Exception {
        given(useCase.getById(ORDER_ID)).willReturn(null);

        mockMvc.perform(get("/api/v1/purchase-orders/{id}", ORDER_ID).with(asRole("AUXILIAR")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("CONTADOR can list purchase orders (GET / -> 200)")
    void contadorCanListOrders() throws Exception {
        given(useCase.list(any(Pageable.class))).willReturn(emptyPage());

        mockMvc.perform(get("/api/v1/purchase-orders").with(asRole("CONTADOR")))
                .andExpect(status().isOk());

        then(useCase).should().list(any(Pageable.class));
    }

    @Test
    @DisplayName("CONTADOR can read a purchase order by id (GET /{id} -> 200)")
    void contadorCanReadOrderById() throws Exception {
        given(useCase.getById(ORDER_ID)).willReturn(null);

        mockMvc.perform(get("/api/v1/purchase-orders/{id}", ORDER_ID).with(asRole("CONTADOR")))
                .andExpect(status().isOk());

        then(useCase).should().getById(ORDER_ID);
    }

    @Test
    @DisplayName("CONTADOR cannot create a purchase order (POST / -> 403)")
    void contadorCannotCreateOrder() throws Exception {
        mockMvc.perform(post("/api/v1/purchase-orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ORDER_BODY)
                        .with(asRole("CONTADOR")))
                .andExpect(status().isForbidden());

        then(useCase).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("CONTADOR cannot update a purchase order (PUT /{id} -> 403)")
    void contadorCannotUpdateOrder() throws Exception {
        mockMvc.perform(put("/api/v1/purchase-orders/{id}", ORDER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ORDER_BODY)
                        .with(asRole("CONTADOR")))
                .andExpect(status().isForbidden());

        then(useCase).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("CONTADOR cannot cancel a purchase order (PATCH /{id}/cancel -> 403)")
    void contadorCannotCancelOrder() throws Exception {
        mockMvc.perform(patch("/api/v1/purchase-orders/{id}/cancel", ORDER_ID)
                        .with(asRole("CONTADOR")))
                .andExpect(status().isForbidden());

        then(useCase).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("AUXILIAR can create a purchase order (POST / -> 201)")
    void auxiliarCanCreateOrder() throws Exception {
        mockMvc.perform(post("/api/v1/purchase-orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ORDER_BODY)
                        .with(asRole("AUXILIAR")))
                .andExpect(status().isCreated());

        then(useCase).should().create(any(), any());
    }

    @Test
    @DisplayName("AUXILIAR can update a purchase order (PUT /{id} -> 200)")
    void auxiliarCanUpdateOrder() throws Exception {
        mockMvc.perform(put("/api/v1/purchase-orders/{id}", ORDER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ORDER_BODY)
                        .with(asRole("AUXILIAR")))
                .andExpect(status().isOk());

        then(useCase).should().update(any(), any());
    }

    @Test
    @DisplayName("AUXILIAR can cancel a purchase order (PATCH /{id}/cancel -> 200)")
    void auxiliarCanCancelOrder() throws Exception {
        mockMvc.perform(patch("/api/v1/purchase-orders/{id}/cancel", ORDER_ID)
                        .with(asRole("AUXILIAR")))
                .andExpect(status().isOk());

        then(useCase).should().cancel(ORDER_ID);
    }
}
