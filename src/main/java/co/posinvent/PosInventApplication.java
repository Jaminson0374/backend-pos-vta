package co.posinvent;

import co.posinvent.infrastructure.config.JwtProperties;
import co.posinvent.infrastructure.config.MediaProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;	
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableAsync
@EnableScheduling
@EnableConfigurationProperties({JwtProperties.class, MediaProperties.class})
public class PosInventApplication {

	public static void main(String[] args) {
		SpringApplication.run(PosInventApplication.class, args);
	}

}
