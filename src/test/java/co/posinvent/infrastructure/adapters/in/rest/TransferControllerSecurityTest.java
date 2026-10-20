package co.posinvent.infrastructure.adapters.in.rest;

import co.posinvent.application.dto.TransferResponse;
import co.posinvent.application.port.in.CancelTransferPort;
import co.posinvent.application.port.in.ConfirmTransferPort;
import co.posinvent.application.port.in.CreateTransferPort;
import co.posinvent.application.port.in.GetTransferPort;
import co.posinvent.application.port.in.ListTransfersPort;
import co.posinvent.application.service.JwtBlacklistService;
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
import org.springframework.data.domain.Page;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;
import java.util.UUID;

import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTTP role matrix for transfers after the S6c.1 change: warehouse reads are available to
 * ADMIN, ALMACENISTA and AUXILIAR; CAJERO and VENDEDOR are denied at the controller.
 */
@WebMvcTest(controllers = TransferController.class)
@ActiveProfiles("test")
@Import({SecurityConfig.class, TransferControllerSecurityTest.JwtFilterTestConfig.class})
class TransferControllerSecurityTest {

    private static final UUID TRANSFER_ID =
            UUID.fromString("99999999-9999-9999-9999-999999999999");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CreateTransferPort createTransfer;

    @MockitoBean
    private ConfirmTransferPort confirmTransfer;

    @MockitoBean
    private CancelTransferPort cancelTransfer;

    @MockitoBean
    private ListTransfersPort listTransfers;

    @MockitoBean
    private GetTransferPort getTransfer;

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
    @DisplayName("ADMIN can list transfers (GET / -> 200)")
    void adminCanListTransfers() throws Exception {
        expectListAllowed("ADMIN");
    }

    @Test
    @DisplayName("ALMACENISTA can list transfers (GET / -> 200)")
    void almacenistaCanListTransfers() throws Exception {
        expectListAllowed("ALMACENISTA");
    }

    @Test
    @DisplayName("AUXILIAR can list transfers (GET / -> 200)")
    void auxiliarCanListTransfers() throws Exception {
        expectListAllowed("AUXILIAR");
    }

    @Test
    @DisplayName("CAJERO cannot list transfers (GET / -> 403)")
    void cajeroCannotListTransfers() throws Exception {
        mockMvc.perform(get("/api/v1/transfers").with(asRole("CAJERO")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("VENDEDOR cannot list transfers (GET / -> 403)")
    void vendedorCannotListTransfers() throws Exception {
        mockMvc.perform(get("/api/v1/transfers").with(asRole("VENDEDOR")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("AUXILIAR can read a transfer (GET /{id} -> 200)")
    void auxiliarCanReadTransfer() throws Exception {
        given(getTransfer.execute(TRANSFER_ID)).willReturn(mockResponse());

        mockMvc.perform(get("/api/v1/transfers/{id}", TRANSFER_ID).with(asRole("AUXILIAR")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("CAJERO cannot read a transfer (GET /{id} -> 403)")
    void cajeroCannotReadTransfer() throws Exception {
        mockMvc.perform(get("/api/v1/transfers/{id}", TRANSFER_ID).with(asRole("CAJERO")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("CAJERO cannot confirm a transfer (POST /{id}/confirm -> 403)")
    void cajeroCannotConfirmTransfer() throws Exception {
        mockMvc.perform(post("/api/v1/transfers/{id}/confirm", TRANSFER_ID)
                        .with(asRole("CAJERO")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("AUXILIAR cannot confirm a transfer (POST /{id}/confirm -> 403)")
    void auxiliarCannotConfirmTransfer() throws Exception {
        mockMvc.perform(post("/api/v1/transfers/{id}/confirm", TRANSFER_ID)
                        .with(asRole("AUXILIAR")))
                .andExpect(status().isForbidden());
    }

    private void expectListAllowed(String role) throws Exception {
        given(listTransfers.execute(0, 20)).willReturn(Page.empty());

        mockMvc.perform(get("/api/v1/transfers").with(asRole(role)))
                .andExpect(status().isOk());
    }

    private static TransferResponse mockResponse() {
        return org.mockito.Mockito.mock(TransferResponse.class);
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
}
