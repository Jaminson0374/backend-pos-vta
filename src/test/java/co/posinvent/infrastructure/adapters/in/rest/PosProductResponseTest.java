package co.posinvent.infrastructure.adapters.in.rest;

import co.posinvent.application.dto.PosProductResponse;
import co.posinvent.domain.model.Product;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * Contract test for the POS projection: sale data is present, cost and margin data is not.
 */
class PosProductResponseTest {

    @Test
    @DisplayName("PosProductResponse serializes sale fields and omits every cost field")
    void posProjectionExcludesCostData() throws Exception {
        var product = mock(Product.class);
        given(product.id()).willReturn(UUID.fromString("99999999-9999-9999-9999-999999999999"));
        given(product.productCode()).willReturn("P-001");
        given(product.name()).willReturn("Lomo de cerdo");
        given(product.taxType()).willReturn("IVA_19");
        given(product.salePrice()).willReturn(new BigDecimal("25000.00"));
        given(product.minStock()).willReturn(new BigDecimal("2.00"));
        given(product.totalStock()).willReturn(new BigDecimal("8.00"));
        given(product.active()).willReturn(true);

        var response = PosProductResponse.from(product);
        var json = new ObjectMapper().writeValueAsString(response);

        assertThat(json)
                .contains("\"salePrice\"")
                .contains("25000.00")
                .contains("\"name\"")
                .contains("Lomo de cerdo")
                .doesNotContain("costPrice")
                .doesNotContain("profitMargin")
                .doesNotContain("suppliers")
                .doesNotContain("priceEntries")
                .doesNotContain("unitCost");
    }
}
