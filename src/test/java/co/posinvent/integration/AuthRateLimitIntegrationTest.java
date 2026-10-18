package co.posinvent.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end proof that the auth rate limiter is wired into the real Spring Security chain.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "spring.profiles.active=test",
                "app.security.rate-limit.login-capacity=2",
                "app.security.rate-limit.login-window-seconds=60",
                "spring.main.allow-bean-definition-overriding=true"
        }
)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthRateLimitIntegrationTest {

    private static final String LOGIN = "/api/v1/auth/login";
    private static final String BODY = """
            {"username":"nobody@example.com","password":"wrong"}
            """;

    @Autowired
    private MockMvc mockMvc;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", AbstractIntegrationTest.postgres::getJdbcUrl);
        registry.add("spring.datasource.username", AbstractIntegrationTest.postgres::getUsername);
        registry.add("spring.datasource.password", AbstractIntegrationTest.postgres::getPassword);
    }

    @Test
    void thirdLoginAttemptThroughRealSecurityChainReturns429() throws Exception {
        var remoteIp = "203.0.113.250";

        mockMvc.perform(post(LOGIN)
                        .with(request -> {
                            request.setRemoteAddr(remoteIp);
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post(LOGIN)
                        .with(request -> {
                            request.setRemoteAddr(remoteIp);
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post(LOGIN)
                        .with(request -> {
                            request.setRemoteAddr(remoteIp);
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.errorCode").value("RATE_LIMITED"))
                .andExpect(jsonPath("$.details").isArray());
    }
}
