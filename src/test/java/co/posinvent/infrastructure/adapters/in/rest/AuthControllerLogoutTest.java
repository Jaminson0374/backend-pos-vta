package co.posinvent.infrastructure.adapters.in.rest;

import co.posinvent.application.service.JwtBlacklistService;
import co.posinvent.application.usecase.AuthenticateUserUseCase;
import co.posinvent.application.usecase.LogoutUseCase;
import co.posinvent.application.usecase.PasswordResetUseCase;
import co.posinvent.infrastructure.config.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice tests for the logout endpoint contract: only a non-blank bearer token
 * reaches the use case, and a successful revocation answers 204.
 */
@WebMvcTest(controllers = AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
class AuthControllerLogoutTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthenticateUserUseCase authenticateUserUseCase;

    @MockitoBean
    private PasswordResetUseCase passwordResetUseCase;

    @MockitoBean
    private LogoutUseCase logoutUseCase;

    // Dependencies of the JwtAuthFilter bean picked up by the slice (filters are disabled).
    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private UserDetailsService userDetailsService;

    @MockitoBean
    private JwtBlacklistService jwtBlacklistService;

    @Test
    void missingAuthorizationHeaderReturnsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(logoutUseCase);
    }

    @Test
    void blankBearerTokenReturnsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout")
                        .header("Authorization", "Bearer   "))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(logoutUseCase);
    }

    @Test
    void validBearerTokenRevokesAndReturnsNoContent() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout")
                        .header("Authorization", "Bearer valid-token"))
                .andExpect(status().isNoContent());

        verify(logoutUseCase).logout("valid-token");
    }
}
