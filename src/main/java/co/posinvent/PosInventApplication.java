package co.posinvent;

import co.posinvent.infrastructure.config.CertificateCryptoProperties;
import co.posinvent.infrastructure.config.DianProviderProperties;
import co.posinvent.infrastructure.config.FactoryProviderProperties;
import co.posinvent.infrastructure.config.JwtProperties;
import co.posinvent.infrastructure.config.MediaProperties;
import co.posinvent.infrastructure.config.RateLimitProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.web.config.EnableSpringDataWebSupport;
import static org.springframework.data.web.config.EnableSpringDataWebSupport.PageSerializationMode.VIA_DTO;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestClient;

@SpringBootApplication
@EnableAsync
@EnableScheduling
@EnableSpringDataWebSupport(pageSerializationMode = VIA_DTO)
@EnableConfigurationProperties({JwtProperties.class, MediaProperties.class, DianProviderProperties.class, FactoryProviderProperties.class, CertificateCryptoProperties.class, RateLimitProperties.class})
public class PosInventApplication {

	public static void main(String[] args) {
		SpringApplication.run(PosInventApplication.class, args);
	}

	/**
	 * Provides the {@link RestClient.Builder} used by HTTP client adapters (for example the
	 * DIAN provider). Spring Boot 4 auto-configures this bean only through the separate
	 * {@code spring-boot-starter-restclient} starter, which this project does not depend on,
	 * so a minimal builder is declared here instead of adding a dependency.
	 */
	@Bean
	RestClient.Builder restClientBuilder() {
		return RestClient.builder();
	}
}