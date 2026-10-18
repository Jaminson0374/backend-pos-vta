package co.posinvent.infrastructure.adapters.in.rest;

import co.posinvent.application.usecase.CreateJournalEntryUseCase;
import co.posinvent.domain.model.JournalEntry;
import co.posinvent.domain.model.JournalEntryLine;
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
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice tests for the manual journal-entry endpoint
 * ({@code POST /api/v1/journal-entries}).
 *
 * <p>A8: the request body is now a typed {@code ManualJournalEntryRequest} and a manual entry
 * MUST carry a justification ({@code @NotBlank}); an explicit manual post without one is
 * rejected with 400 before the use case runs.</p>
 */
@WebMvcTest(controllers = JournalEntryController.class)
@ActiveProfiles("test")
@Import({SecurityConfig.class, JournalEntryControllerTest.JwtFilterTestConfig.class})
class JournalEntryControllerTest {

    private static final UUID DEBIT_ACCOUNT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID CREDIT_ACCOUNT = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CreateJournalEntryUseCase useCase;

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

    private static String bodyWith(String justification) {
        return """
                {
                  "entryDate": "2026-03-14",
                  "description": "Ajuste de cierre",
                  "justification": %s,
                  "lines": [
                    {"accountId": "%s", "debit": 50.00, "credit": 0, "description": "debit"},
                    {"accountId": "%s", "debit": 0, "credit": 50.00, "description": "credit"}
                  ]
                }
                """.formatted(justification == null ? "null" : "\"" + justification + "\"",
                        DEBIT_ACCOUNT, CREDIT_ACCOUNT);
    }

    private static JournalEntry posted(String justification) {
        return new JournalEntry(UUID.randomUUID(), "JE-20260314-000001",
                LocalDate.of(2026, 3, 14), "Ajuste de cierre", "MANUAL", null, null, null, justification,
                List.of(
                        new JournalEntryLine(null, null, DEBIT_ACCOUNT, new BigDecimal("50.00"),
                                BigDecimal.ZERO, "debit"),
                        new JournalEntryLine(null, null, CREDIT_ACCOUNT, BigDecimal.ZERO,
                                new BigDecimal("50.00"), "credit")));
    }

    @Test
    @DisplayName("ADMIN can post a justified manual entry (POST -> 200)")
    void adminCanPostJustifiedManualEntry() throws Exception {
        given(useCase.createManual(any(), any(), eq("Ajuste de cierre mensual"), any()))
                .willReturn(posted("Ajuste de cierre mensual"));

        mockMvc.perform(post("/api/v1/journal-entries")
                        .with(asRole("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyWith("Ajuste de cierre mensual")))
                .andExpect(status().isOk());

        then(useCase).should().createManual(any(), any(), eq("Ajuste de cierre mensual"), any());
    }

    @Test
    @DisplayName("CONTADOR can post a justified manual entry (POST -> 200)")
    void contadorCanPostJustifiedManualEntry() throws Exception {
        given(useCase.createManual(any(), any(), eq("Ajuste de cierre mensual"), any()))
                .willReturn(posted("Ajuste de cierre mensual"));

        mockMvc.perform(post("/api/v1/journal-entries")
                        .with(asRole("CONTADOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyWith("Ajuste de cierre mensual")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("A manual entry without justification is rejected (POST -> 400)")
    void manualEntryWithoutJustificationIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/journal-entries")
                        .with(asRole("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyWith("")))
                .andExpect(status().isBadRequest());

        then(useCase).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("A non-accounting role cannot post a manual entry (POST -> 403)")
    void nonAccountingRoleCannotPostManualEntry() throws Exception {
        mockMvc.perform(post("/api/v1/journal-entries")
                        .with(asRole("CAJERO"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyWith("Ajuste de cierre mensual")))
                .andExpect(status().isForbidden());

        then(useCase).shouldHaveNoInteractions();
    }
}
