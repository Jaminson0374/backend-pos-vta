package co.posinvent.infrastructure.adapters.in.rest;

import co.posinvent.application.dto.PageResponse;
import co.posinvent.application.dto.ProductResponse;
import co.posinvent.application.usecase.ProductUseCase;
import co.posinvent.domain.model.Product;
import co.posinvent.domain.repository.ProductFormulaRepository;
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
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice tests for the product role matrix. Reads are denied to {@code CAJERO} and
 * {@code VENDEDOR}; writes stay {@code ADMIN}-only.
 */
@WebMvcTest(controllers = ProductController.class)
@ActiveProfiles("test")
@Import({SecurityConfig.class, ProductControllerSecurityTest.JwtFilterTestConfig.class})
class ProductControllerSecurityTest {

    private static final String CREATE_BODY = """
            {
              "productCode": "P-001",
              "name": "Producto de prueba",
              "costPrice": 10.00,
              "manufacturedInHouse": false,
              "costAffectingExp": false,
              "manageLots": false,
              "perishable": false,
              "belongsToProduct": false,
              "sellBelowMin": false,
              "inventoriable": true,
              "version": 0
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProductUseCase useCase;

    @MockitoBean
    private ProductFormulaRepository formulaRepository;

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

    private void expectListAllowed(String role) throws Exception {
        given(useCase.list(any())).willReturn(new PageResponse<>(List.of(), 0, 20, 0, 0, true));

        mockMvc.perform(get("/api/v1/products").with(asRole(role)))
                .andExpect(status().isOk());

        then(useCase).should().list(any());
    }

    @Test
    @DisplayName("ADMIN can list products (GET / -> 200)")
    void adminCanListProducts() throws Exception {
        expectListAllowed("ADMIN");
    }

    @Test
    @DisplayName("AUXILIAR can list products (GET / -> 200)")
    void auxiliarCanListProducts() throws Exception {
        expectListAllowed("AUXILIAR");
    }

    @Test
    @DisplayName("ALMACENISTA can list products (GET / -> 200)")
    void almacenistaCanListProducts() throws Exception {
        expectListAllowed("ALMACENISTA");
    }

    @Test
    @DisplayName("CARNICERO can list products (GET / -> 200)")
    void carniceroCanListProducts() throws Exception {
        expectListAllowed("CARNICERO");
    }

    @Test
    @DisplayName("CONTADOR can list products (GET / -> 200)")
    void contadorCanListProducts() throws Exception {
        expectListAllowed("CONTADOR");
    }

    @Test
    @DisplayName("CAJERO cannot list products (GET / -> 403)")
    void cajeroCannotListProducts() throws Exception {
        mockMvc.perform(get("/api/v1/products").with(asRole("CAJERO")))
                .andExpect(status().isForbidden());

        then(useCase).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("VENDEDOR cannot list products (GET / -> 403)")
    void vendedorCannotListProducts() throws Exception {
        mockMvc.perform(get("/api/v1/products").with(asRole("VENDEDOR")))
                .andExpect(status().isForbidden());

        then(useCase).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("ADMIN can create a product (POST / -> 201)")
    void adminCanCreateProduct() throws Exception {
        var product = mock(Product.class);
        var response = ProductResponse.from(product);
        given(useCase.create(any())).willReturn(response);

        mockMvc.perform(post("/api/v1/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_BODY)
                        .with(asRole("ADMIN")))
                .andExpect(status().isCreated());

        then(useCase).should().create(any());
    }

    @Test
    @DisplayName("ALMACENISTA cannot create a product (POST / -> 403)")
    void almacenistaCannotCreateProduct() throws Exception {
        mockMvc.perform(post("/api/v1/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_BODY)
                        .with(asRole("ALMACENISTA")))
                .andExpect(status().isForbidden());

        then(useCase).shouldHaveNoInteractions();
    }
}
