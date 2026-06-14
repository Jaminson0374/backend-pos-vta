package co.posinvent.application.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProductRequestValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    void validate_cascadesIntoNestedCollections() {
        var request = request(
                List.of(),
                List.of(new ProductRequest.ProductImageRequest(null, "x".repeat(501), 0)),
                List.of(),
                List.of()
        );

        var violations = validator.validate(request);

        assertThat(violations)
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("images[0].imageUrl");
    }

    @Test
    void validate_cascadesIntoSuppliers() {
        var request = request(
                List.of(new ProductRequest.ProductSupplierRequest(null, null, "x".repeat(101), BigDecimal.ONE, false)),
                List.of(),
                List.of(),
                List.of()
        );

        var violations = validator.validate(request);

        assertThat(violations)
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("suppliers[0].supplierReference");
    }

    @Test
    void validate_cascadesIntoPromotions() {
        var request = request(
                List.of(),
                List.of(),
                List.of(new ProductRequest.ProductPromotionRequest(null, null, new BigDecimal("101"), null, null, false)),
                List.of()
        );

        var violations = validator.validate(request);

        assertThat(violations)
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("promotions[0].discountPct");
    }

    @Test
    void validate_cascadesIntoPriceEntries() {
        var request = request(
                List.of(),
                List.of(),
                List.of(),
                List.of(new ProductRequest.ProductPriceEntryRequest(null, null, new BigDecimal("-1"), BigDecimal.ZERO))
        );

        var violations = validator.validate(request);

        assertThat(violations)
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("priceEntries[0].price");
    }

    private ProductRequest request(
            List<ProductRequest.ProductSupplierRequest> suppliers,
            List<ProductRequest.ProductImageRequest> images,
            List<ProductRequest.ProductPromotionRequest> promotions,
            List<ProductRequest.ProductPriceEntryRequest> priceEntries) {

        return new ProductRequest(
                "P-001",
                "Producto test",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                BigDecimal.ONE,
                new BigDecimal("10.00"),
                "IVA_19",
                "PEPS",
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.TEN,
                false,
                false,
                false,
                false,
                false,
                false,
                true,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                0,
                List.of(),
                suppliers,
                images,
                promotions,
                priceEntries
        );
    }
}
