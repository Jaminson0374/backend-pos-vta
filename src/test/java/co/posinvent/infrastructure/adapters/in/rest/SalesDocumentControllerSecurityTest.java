package co.posinvent.infrastructure.adapters.in.rest;

import co.posinvent.application.dto.PageResponse;
import co.posinvent.application.dto.SalesDocumentResponse;
import co.posinvent.application.usecase.ManageSalesDocumentUseCase;
import co.posinvent.application.service.JwtBlacklistService;
import co.posinvent.domain.model.SalesDocument;
import co.posinvent.domain.model.SalesDocumentStatus;
import co.posinvent.domain.model.SalesDocumentType;
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

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Role matrix for sales-document reads: CONTADOR gains read-only fiscal access, while every
 * mutation endpoint keeps denying CONTADOR and VENDEDOR before the use case is reached.
 */
@WebMvcTest(controllers = SalesDocumentController.class)
@ActiveProfiles("test")
@Import({SecurityConfig.class, SalesDocumentControllerSecurityTest.JwtFilterTestConfig.class})
class SalesDocumentControllerSecurityTest {

    private static final UUID DOCUMENT_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID ITEM_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID CLIENT_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");
    private static final UUID WAREHOUSE_ID = UUID.fromString("66666666-6666-6666-6666-666666666666");
    private static final UUID PRODUCT_ID = UUID.fromString("77777777-7777-7777-7777-777777777777");

    private static final String CREATE_DOCUMENT_BODY = """
            {
              "type": "ORDER",
              "clientId": "%s",
              "warehouseId": "%s"
            }
            """.formatted(CLIENT_ID, WAREHOUSE_ID);

    private static final String ITEM_BODY = """
            {
              "productId": "%s",
              "quantity": 1,
              "unitPrice": 100.00,
              "taxType": "EXENTO"
            }
            """.formatted(PRODUCT_ID);

    private static final String TRANSITION_BODY = """
            { "targetStatus": "CONFIRMED" }
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ManageSalesDocumentUseCase useCase;

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

    @Test
    @DisplayName("CONTADOR can list sales documents (GET /documents -> 200)")
    void contadorCanListDocuments() throws Exception {
        given(useCase.getDocuments(anyInt(), anyInt(), any(), any()))
                .willReturn(new PageResponse<>(List.of(), 0, 20, 0, 0, true));

        mockMvc.perform(get("/api/v1/sales/documents").with(asRole("CONTADOR")))
                .andExpect(status().isOk());

        then(useCase).should().getDocuments(0, 20, null, null);
    }

    @Test
    @DisplayName("CONTADOR can read a sales document by id (GET /documents/{id} -> 200)")
    void contadorCanReadDocumentById() throws Exception {
        given(useCase.getById(DOCUMENT_ID)).willReturn(documentResponse());

        mockMvc.perform(get("/api/v1/sales/documents/{id}", DOCUMENT_ID).with(asRole("CONTADOR")))
                .andExpect(status().isOk());

        then(useCase).should().getById(DOCUMENT_ID);
    }

    @Test
    @DisplayName("CAJERO can list sales documents (GET /documents -> 200)")
    void cajeroCanListDocuments() throws Exception {
        given(useCase.getDocuments(anyInt(), anyInt(), any(), any()))
                .willReturn(new PageResponse<>(List.of(), 0, 20, 0, 0, true));

        mockMvc.perform(get("/api/v1/sales/documents").with(asRole("CAJERO")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("VENDEDOR cannot list sales documents (GET /documents -> 403)")
    void vendedorCannotListDocuments() throws Exception {
        mockMvc.perform(get("/api/v1/sales/documents").with(asRole("VENDEDOR")))
                .andExpect(status().isForbidden());

        then(useCase).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("CONTADOR cannot create a document (POST /documents -> 403)")
    void contadorCannotCreateDocument() throws Exception {
        mockMvc.perform(post("/api/v1/sales/documents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_DOCUMENT_BODY)
                        .with(asRole("CONTADOR")))
                .andExpect(status().isForbidden());

        then(useCase).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("CONTADOR cannot update an item (PUT /items/{id} -> 403)")
    void contadorCannotUpdateItem() throws Exception {
        mockMvc.perform(put("/api/v1/sales/items/{id}", ITEM_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ITEM_BODY)
                        .with(asRole("CONTADOR")))
                .andExpect(status().isForbidden());

        then(useCase).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("CONTADOR cannot delete an item (DELETE /items/{id} -> 403)")
    void contadorCannotDeleteItem() throws Exception {
        mockMvc.perform(delete("/api/v1/sales/items/{id}", ITEM_ID).with(asRole("CONTADOR")))
                .andExpect(status().isForbidden());

        then(useCase).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("CONTADOR cannot transition a document (POST /documents/{id}/transition -> 403)")
    void contadorCannotTransitionDocument() throws Exception {
        mockMvc.perform(post("/api/v1/sales/documents/{id}/transition", DOCUMENT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(TRANSITION_BODY)
                        .with(asRole("CONTADOR")))
                .andExpect(status().isForbidden());

        then(useCase).shouldHaveNoInteractions();
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

    private static SalesDocumentResponse documentResponse() {
        var document = new SalesDocument(
                DOCUMENT_ID, SalesDocumentType.ORDER, SalesDocumentStatus.DRAFT, "ORD-1",
                CLIENT_ID, WAREHOUSE_ID, null, null, null,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                UUID.randomUUID(), null, null, List.of(), null, false, null);
        return SalesDocumentResponse.from(document);
    }
}
