package co.posinvent;

import co.posinvent.integration.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Context smoke test. Runs against an isolated Testcontainers database (via
 * {@link AbstractIntegrationTest}) instead of the local dev database, so it no longer depends on
 * the developer's Flyway schema state.
 */
class PosInventApplicationTests extends AbstractIntegrationTest {

	@TestConfiguration
	static class TestConfig {
		@Bean
		@Primary
		ObjectMapper objectMapper() {
			return new ObjectMapper();
		}
	}

	@Test
	void contextLoads() {
	}
}
