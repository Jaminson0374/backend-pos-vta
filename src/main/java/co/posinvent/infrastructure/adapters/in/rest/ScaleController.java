package co.posinvent.infrastructure.adapters.in.rest;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

@RestController
@RequestMapping("/api/v1/scale")
@PreAuthorize("hasAnyRole('ADMIN','CAJERO','CARNICERO','ALMACENISTA','AUXILIAR')")
public class ScaleController {

    private final Random random = ThreadLocalRandom.current();

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        double weight = BigDecimal.valueOf(0.5 + random.nextDouble() * 24.5)
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();

        Map<String, Object> response = Map.of(
                "weight", weight,
                "unit", "kg",
                "stable", true,
                "timestamp", Instant.now().toString()
        );

        return ResponseEntity.ok(response);
    }
}
