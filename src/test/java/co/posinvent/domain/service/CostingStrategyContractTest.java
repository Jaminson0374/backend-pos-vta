package co.posinvent.domain.service;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Architecture contract guard (REQ-COST-001 / REQ-COST-007): the costing domain
 * ({@code co.posinvent.domain.service}) MUST remain JPA/Spring-free. Strategies
 * import neither {@code application} nor {@code infrastructure}, and contain no
 * {@code @Transactional}/{@code @Service}/JPA markers.
 */
class CostingStrategyContractTest {

    private static final Path DOMAIN_SERVICE_SRC = Path.of(
            "src/main/java/co/posinvent/domain/service");

    private static final List<String> SOURCE_FILES = List.of(
            "CostingMethod.java",
            "CostingStrategy.java",
            "StockEntry.java",
            "EntryCostingResult.java",
            "CostConsumptionResult.java",
            "FifoCostingStrategy.java",
            "WeightedAvgCostingStrategy.java"
    );

    @Test
    void domainStrategiesImportNeitherApplicationNorInfrastructure() throws IOException {
        for (String file : SOURCE_FILES) {
            String content = readSource(file);

            assertThat(content)
                    .as("%s must not import application packages", file)
                    .doesNotContain("import co.posinvent.application");
            assertThat(content)
                    .as("%s must not import infrastructure packages", file)
                    .doesNotContain("import co.posinvent.infrastructure");
        }
    }

    @Test
    void domainStrategiesContainNoSpringOrJpaMarkers() throws IOException {
        for (String file : SOURCE_FILES) {
            String content = readSource(file);

            assertThat(content)
                    .as("%s must be Spring/JPA-free", file)
                    .doesNotContain("@Transactional")
                    .doesNotContain("@Service")
                    .doesNotContain("@Repository")
                    .doesNotContain("@Component")
                    .doesNotContain("jakarta.persistence")
                    .doesNotContain("org.springframework");
        }
    }

    private static String readSource(String file) throws IOException {
        return Files.readString(DOMAIN_SERVICE_SRC.resolve(file));
    }
}
