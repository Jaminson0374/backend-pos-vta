package co.posinvent.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class AuthRateLimitFilterTest {

    private static final String LOGIN = "/api/v1/auth/login";
    private static final String FORGOT = "/api/v1/auth/forgot-password";
    private static final String SET_PASSWORD = "/api/v1/auth/set-password";

    private final ObjectMapper objectMapper = new ObjectMapper();

    private AuthRateLimitFilter filter(boolean trustForwardedHeaders) {
        return new AuthRateLimitFilter(
                new RateLimitProperties(trustForwardedHeaders, 2, 60, 2, 60, 2, 60),
                objectMapper);
    }

    private MockHttpServletResponse attempt(
            AuthRateLimitFilter filter, String method, String path, String remoteAddr) throws Exception {
        return attempt(filter, method, path, remoteAddr, null);
    }

    private MockHttpServletResponse attempt(
            AuthRateLimitFilter filter, String method, String path, String remoteAddr,
            String forwardedFor) throws Exception {
        var request = new MockHttpServletRequest(method, path);
        request.setRemoteAddr(remoteAddr);
        if (forwardedFor != null) {
            request.addHeader("X-Forwarded-For", forwardedFor);
        }
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }

    @Test
    void thirdLoginAttemptFromSameIpIsRateLimited() throws Exception {
        var filter = filter(false);

        assertThat(attempt(filter, "POST", LOGIN, "10.0.0.1").getStatus()).isEqualTo(200);
        assertThat(attempt(filter, "POST", LOGIN, "10.0.0.1").getStatus()).isEqualTo(200);

        var limited = attempt(filter, "POST", LOGIN, "10.0.0.1");
        assertThat(limited.getStatus()).isEqualTo(429);
        assertThat(limited.getHeader("Retry-After")).isNotNull();
        assertThat(Long.parseLong(limited.getHeader("Retry-After"))).isPositive();

        var body = objectMapper.readTree(limited.getContentAsString());
        assertThat(body.get("errorCode").asText()).isEqualTo("RATE_LIMITED");
        assertThat(body.get("details").isArray()).isTrue();
        assertThat(body.get("details")).isEmpty();
    }

    @Test
    void bucketsAreIndependentPerIp() throws Exception {
        var filter = filter(false);

        attempt(filter, "POST", LOGIN, "10.0.0.1");
        attempt(filter, "POST", LOGIN, "10.0.0.1");
        assertThat(attempt(filter, "POST", LOGIN, "10.0.0.1").getStatus()).isEqualTo(429);

        assertThat(attempt(filter, "POST", LOGIN, "10.0.0.2").getStatus()).isEqualTo(200);
    }

    @Test
    void forwardedForAddressesGetIndependentBucketsWhenTrusted() throws Exception {
        var filter = filter(true);

        attempt(filter, "POST", FORGOT, "10.0.0.1", "203.0.113.10, 10.0.0.1");
        attempt(filter, "POST", FORGOT, "10.0.0.1", "203.0.113.10, 10.0.0.1");
        assertThat(attempt(filter, "POST", FORGOT, "10.0.0.1", "203.0.113.10, 10.0.0.1").getStatus())
                .isEqualTo(429);

        assertThat(attempt(filter, "POST", FORGOT, "10.0.0.1", "198.51.100.20, 10.0.0.1").getStatus())
                .isEqualTo(200);
    }

    @Test
    void forwardedForIsIgnoredWhenNotTrusted() throws Exception {
        var filter = filter(false);

        attempt(filter, "POST", SET_PASSWORD, "10.0.0.1", "203.0.113.10");
        attempt(filter, "POST", SET_PASSWORD, "10.0.0.1", "198.51.100.20");

        assertThat(attempt(filter, "POST", SET_PASSWORD, "10.0.0.1", "192.0.2.30").getStatus())
                .isEqualTo(429);
    }

    @Test
    void nonAuthPathAndGetRequestsAreNotLimited() throws Exception {
        var filter = filter(false);

        for (int i = 0; i < 10; i++) {
            assertThat(attempt(filter, "POST", "/api/v1/users", "10.0.0.1").getStatus()).isEqualTo(200);
            assertThat(attempt(filter, "GET", LOGIN, "10.0.0.1").getStatus()).isEqualTo(200);
        }
    }
}
