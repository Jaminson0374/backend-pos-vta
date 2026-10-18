package co.posinvent.infrastructure.adapters.in.rest;

import co.posinvent.application.usecase.ManageDebitCreditNoteUseCase;
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.then;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice tests for the debit/credit-note role matrix (M8).
 *
 * <p>Reads are open to {@code ADMIN}, {@code AUXILIAR} and {@code CONTADOR}; writes are financial
 * and restricted to {@code ADMIN} and {@code CONTADOR}. An {@code AUXILIAR} write gets 403 and the
 * use case is never reached.</p>
 */
@WebMvcTest(controllers = DebitCreditNoteController.class)
@ActiveProfiles("test")
@Import({SecurityConfig.class, DebitCreditNoteControllerSecurityTest.JwtFilterTestConfig.class})
class DebitCreditNoteControllerSecurityTest {

    private static final UUID NOTE_ID = UUID.fromString("66666666-6666-6666-6666-666666666666");

    private static final String NOTE_BODY = """
            {
              "type": "DEBIT_NOTE",
              "supplierId": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
              "amount": 50.00,
              "reason": "ajuste"
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ManageDebitCreditNoteUseCase useCase;

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
    @DisplayName("AUXILIAR can list debit/credit notes (GET / -> 200)")
    void auxiliarCanListNotes() throws Exception {
        mockMvc.perform(get("/api/v1/debit-credit-notes").with(asRole("AUXILIAR")))
                .andExpect(status().isOk());

        then(useCase).should().list(any(), any(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("AUXILIAR can read a debit/credit note by id (GET /{id} -> 200)")
    void auxiliarCanReadNoteById() throws Exception {
        mockMvc.perform(get("/api/v1/debit-credit-notes/{id}", NOTE_ID).with(asRole("AUXILIAR")))
                .andExpect(status().isOk());

        then(useCase).should().getById(NOTE_ID);
    }

    @Test
    @DisplayName("CONTADOR can create a debit/credit note (POST / -> 201)")
    void contadorCanCreateNote() throws Exception {
        mockMvc.perform(post("/api/v1/debit-credit-notes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(NOTE_BODY)
                        .with(asRole("CONTADOR")))
                .andExpect(status().isCreated());

        then(useCase).should().create(any());
    }

    @Test
    @DisplayName("AUXILIAR cannot create a debit/credit note (POST / -> 403)")
    void auxiliarCannotCreateNote() throws Exception {
        mockMvc.perform(post("/api/v1/debit-credit-notes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(NOTE_BODY)
                        .with(asRole("AUXILIAR")))
                .andExpect(status().isForbidden());

        then(useCase).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("CONTADOR can update a debit/credit note (PUT /{id} -> 200)")
    void contadorCanUpdateNote() throws Exception {
        mockMvc.perform(put("/api/v1/debit-credit-notes/{id}", NOTE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(NOTE_BODY)
                        .with(asRole("CONTADOR")))
                .andExpect(status().isOk());

        then(useCase).should().update(eq(NOTE_ID), any());
    }

    @Test
    @DisplayName("AUXILIAR cannot update a debit/credit note (PUT /{id} -> 403)")
    void auxiliarCannotUpdateNote() throws Exception {
        mockMvc.perform(put("/api/v1/debit-credit-notes/{id}", NOTE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(NOTE_BODY)
                        .with(asRole("AUXILIAR")))
                .andExpect(status().isForbidden());

        then(useCase).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("CONTADOR can delete a debit/credit note (DELETE /{id} -> 204)")
    void contadorCanDeleteNote() throws Exception {
        mockMvc.perform(delete("/api/v1/debit-credit-notes/{id}", NOTE_ID)
                        .with(asRole("CONTADOR")))
                .andExpect(status().isNoContent());

        then(useCase).should().delete(NOTE_ID);
    }

    @Test
    @DisplayName("AUXILIAR cannot delete a debit/credit note (DELETE /{id} -> 403)")
    void auxiliarCannotDeleteNote() throws Exception {
        mockMvc.perform(delete("/api/v1/debit-credit-notes/{id}", NOTE_ID)
                        .with(asRole("AUXILIAR")))
                .andExpect(status().isForbidden());

        then(useCase).shouldHaveNoInteractions();
    }
}
