package co.posinvent.infrastructure.adapters.in.rest;

import co.posinvent.application.usecase.CollectionUseCase;
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
import org.springframework.data.domain.Page;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice tests for the collections role matrix. Reads and contact logging are allowed for
 * {@code ADMIN}, {@code CONTADOR} and {@code CAJERO}; warehouse and meat-processing roles are denied.
 */
@WebMvcTest(controllers = CollectionController.class)
@ActiveProfiles("test")
@Import({SecurityConfig.class, CollectionControllerSecurityTest.JwtFilterTestConfig.class})
class CollectionControllerSecurityTest {

    private static final UUID COLLECTION_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CollectionUseCase useCase;

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

    @Test
    @DisplayName("ADMIN can list collections (GET / -> 200)")
    void adminCanListCollections() throws Exception {
        given(useCase.list(any(), any(), any())).willReturn(Page.empty());

        mockMvc.perform(get("/api/v1/collections").with(asRole("ADMIN")))
                .andExpect(status().isOk());

        then(useCase).should().list(any(), any(), any());
    }

    @Test
    @DisplayName("CONTADOR can list collections (GET / -> 200)")
    void contadorCanListCollections() throws Exception {
        given(useCase.list(any(), any(), any())).willReturn(Page.empty());

        mockMvc.perform(get("/api/v1/collections").with(asRole("CONTADOR")))
                .andExpect(status().isOk());

        then(useCase).should().list(any(), any(), any());
    }

    @Test
    @DisplayName("CAJERO can list collections (GET / -> 200)")
    void cajeroCanListCollections() throws Exception {
        given(useCase.list(any(), any(), any())).willReturn(Page.empty());

        mockMvc.perform(get("/api/v1/collections").with(asRole("CAJERO")))
                .andExpect(status().isOk());

        then(useCase).should().list(any(), any(), any());
    }

    @Test
    @DisplayName("ALMACENISTA cannot list collections (GET / -> 403)")
    void almacenistaCannotListCollections() throws Exception {
        mockMvc.perform(get("/api/v1/collections").with(asRole("ALMACENISTA")))
                .andExpect(status().isForbidden());

        then(useCase).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("CAJERO can log a contact (POST /{id}/contact -> 200)")
    void cajeroCanLogContact() throws Exception {
        mockMvc.perform(post("/api/v1/collections/{id}/contact", COLLECTION_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"contactMethod":"PHONE","contactNotes":"Llamado"}
                                """)
                        .with(asRole("CAJERO")))
                .andExpect(status().isOk());

        then(useCase).should().logContact(any(), any());
    }

    @Test
    @DisplayName("CARNICERO cannot log a contact (POST /{id}/contact -> 403)")
    void carniceroCannotLogContact() throws Exception {
        mockMvc.perform(post("/api/v1/collections/{id}/contact", COLLECTION_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"contactMethod":"PHONE","contactNotes":"Llamado"}
                                """)
                        .with(asRole("CARNICERO")))
                .andExpect(status().isForbidden());

        then(useCase).shouldHaveNoInteractions();
    }
}
