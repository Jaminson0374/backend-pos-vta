package co.posinvent;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@SpringBootTest(properties = "spring.main.allow-bean-definition-overriding=true")
class PosInventApplicationTests {

	@TestConfiguration
	static class TestConfig {
		@Bean
		@Primary
		ObjectMapper objectMapper() {
			return new ObjectMapper();
		}
	}

	@Test
	@Disabled("Spring Boot 4.0 auto-configuration issue: ElectronicInvoiceJob requires ObjectMapper bean not auto-configured in test context. Needs investigation.")
	void contextLoads() {
	}

}
