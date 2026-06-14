package co.posinvent.domain.model;

import co.posinvent.domain.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductTest {

    @Test
    void validate_rejectsDuplicateWarehouseIdentity() {
        var warehouseId = UUID.randomUUID();
        var product = product(
                List.of(
                        new Product.ProductWarehouse(null, warehouseId, null, null, false),
                        new Product.ProductWarehouse(null, warehouseId, UUID.randomUUID(), UUID.randomUUID(), true)
                ),
                List.of(),
                List.of(),
                List.of(),
                List.of()
        );

        assertThatThrownBy(product::validate)
                .isInstanceOf(BusinessException.class)
                .hasMessage("El producto no puede repetir la misma bodega.");
    }

    @Test
    void validate_rejectsMultipleMainSuppliers() {
        var product = product(
                List.of(),
                List.of(
                        new Product.ProductSupplier(null, UUID.randomUUID(), "A", BigDecimal.ONE, true),
                        new Product.ProductSupplier(null, UUID.randomUUID(), "B", BigDecimal.TEN, true)
                ),
                List.of(),
                List.of(),
                List.of()
        );

        assertThatThrownBy(product::validate)
                .isInstanceOf(BusinessException.class)
                .hasMessage("El producto solo puede tener un proveedor principal.");
    }

    @Test
    void validate_rejectsDuplicateImageDisplayOrder() {
        var product = product(
                List.of(),
                List.of(),
                List.of(
                        new Product.ProductImage(null, "https://example.com/1.png", 0),
                        new Product.ProductImage(null, "https://example.com/2.png", 0)
                ),
                List.of(),
                List.of()
        );

        assertThatThrownBy(product::validate)
                .isInstanceOf(BusinessException.class)
                .hasMessage("El producto no puede repetir el orden de imagen.");
    }

    @Test
    void validate_rejectsPromotionDateRangeInconsistency() {
        var product = product(
                List.of(),
                List.of(),
                List.of(),
                List.of(new Product.ProductPromotion(
                        null,
                        "Promo",
                        new BigDecimal("10.00"),
                        LocalDate.of(2026, 5, 10),
                        LocalDate.of(2026, 5, 1),
                        true
                )),
                List.of()
        );

        assertThatThrownBy(product::validate)
                .isInstanceOf(BusinessException.class)
                .hasMessage("La fecha inicial de la promocion no puede ser mayor a la fecha final.");
    }

    @Test
    void validate_rejectsDuplicatePriceListIdentity() {
        var priceListId = UUID.randomUUID();
        var product = product(
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(
                        new Product.ProductPriceEntry(null, priceListId, Product.calculateSalePrice(BigDecimal.ONE, new BigDecimal("10.00"), "IVA_19"), new BigDecimal("10.00")),
                        new Product.ProductPriceEntry(null, priceListId, Product.calculateSalePrice(BigDecimal.ONE, new BigDecimal("20.00"), "IVA_19"), new BigDecimal("20.00"))
                )
        );

        assertThatThrownBy(product::validate)
                .isInstanceOf(BusinessException.class)
                .hasMessage("El producto no puede repetir la misma lista de precios.");
    }

    @Test
    void validate_rejectsInconsistentPriceEntryPrice() {
        var product = product(
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(new Product.ProductPriceEntry(
                        null,
                        UUID.randomUUID(),
                        new BigDecimal("100.00"),
                        new BigDecimal("25.00")
                ))
        );

        assertThatThrownBy(product::validate)
                .isInstanceOf(BusinessException.class)
                .hasMessage("Cada precio por lista debe coincidir con el costo, margen e impuesto del producto.")
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo("INCONSISTENT_PRODUCT_PRICE_ENTRY"));
    }

    @Test
    void validate_rejectsInconsistentSalePrice() {
        var now = OffsetDateTime.now();
        var product = new Product(
                UUID.randomUUID(),
                "P-001",
                "Producto test",
                "770000000001",
                "REF-001",
                "Producto para pruebas",
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
                new BigDecimal("1.1000"),
                "PEPS",
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.TEN,
                BigDecimal.ZERO,
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
                true,
                0,
                now,
                now,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of()
        );

        assertThatThrownBy(product::validate)
                .isInstanceOf(BusinessException.class)
                .hasMessage("El precio de venta no coincide con el costo, margen e impuesto del producto.")
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo("INCONSISTENT_SALE_PRICE"));
    }

    @Test
    void validate_rejectsModelWithoutBrand() {
        var product = product(
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                UUID.randomUUID(),
                null,
                null,
                null,
                BigDecimal.ZERO,
                BigDecimal.TEN
        );

        assertThatThrownBy(product::validate)
                .isInstanceOf(BusinessException.class)
                .hasMessage("El modelo requiere una marca asociada.")
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo("INVALID_PRODUCT_MODEL"));
    }

    @Test
    void validate_rejectsGroupWithoutCategory() {
        var product = product(
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                null,
                null,
                UUID.randomUUID(),
                null,
                BigDecimal.ZERO,
                BigDecimal.TEN
        );

        assertThatThrownBy(product::validate)
                .isInstanceOf(BusinessException.class)
                .hasMessage("El grupo requiere una categoria asociada.")
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo("INVALID_PRODUCT_GROUP"));
    }

    @Test
    void validate_rejectsInvalidStockRange() {
        var product = product(
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                null,
                null,
                null,
                null,
                new BigDecimal("10.00"),
                new BigDecimal("5.00")
        );

        assertThatThrownBy(product::validate)
                .isInstanceOf(BusinessException.class)
                .hasMessage("El stock maximo no puede ser menor al stock minimo.")
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo("INVALID_STOCK_RANGE"));
    }

    @Test
    void constructor_appliesBackendDefaultsForPricingAndStockFields() {
        var now = OffsetDateTime.now();
        var costPrice = new BigDecimal("100.00");
        var product = new Product(
                UUID.randomUUID(),
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
                costPrice,
                null,
                null,
                Product.calculateSalePrice(costPrice, null, null),
                null,
                null,
                null,
                null,
                null,
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
                true,
                0,
                now,
                now,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of()
        );

        assertThat(product.profitMargin()).isEqualByComparingTo("0");
        assertThat(product.taxType()).isEqualTo("EXENTO");
        assertThat(product.costingMethod()).isEqualTo("PROMEDIO_PONDERADO");
        assertThat(product.initialStock()).isEqualByComparingTo("0");
        assertThat(product.minStock()).isEqualByComparingTo("0");
        assertThat(product.maxStock()).isEqualByComparingTo("0");
        assertThat(product.salePrice()).isEqualByComparingTo("100.0000");
        assertThatCode(product::validate).doesNotThrowAnyException();
    }

    @Test
    void validate_acceptsConsistentAggregate() {
        var entryMargin = new BigDecimal("25.00");
        var product = product(
                List.of(new Product.ProductWarehouse(null, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), true)),
                List.of(new Product.ProductSupplier(null, UUID.randomUUID(), "REF", new BigDecimal("15.00"), true)),
                List.of(new Product.ProductImage(null, "https://example.com/image.png", 0)),
                List.of(new Product.ProductPromotion(
                        null,
                        "Promo valida",
                        new BigDecimal("10.00"),
                        LocalDate.of(2026, 5, 1),
                        LocalDate.of(2026, 5, 31),
                        true
                )),
                List.of(new Product.ProductPriceEntry(
                        null,
                        UUID.randomUUID(),
                        Product.calculateSalePrice(BigDecimal.ONE, entryMargin, "IVA_19"),
                        entryMargin))
        );

        assertThatCode(product::validate).doesNotThrowAnyException();
    }

    private Product product(
            List<Product.ProductWarehouse> warehouses,
            List<Product.ProductSupplier> suppliers,
            List<Product.ProductImage> images,
            List<Product.ProductPromotion> promotions,
            List<Product.ProductPriceEntry> priceEntries) {
        return product(
                warehouses,
                suppliers,
                images,
                promotions,
                priceEntries,
                null,
                null,
                null,
                null,
                BigDecimal.ZERO,
                BigDecimal.TEN
        );
    }

    private Product product(
            List<Product.ProductWarehouse> warehouses,
            List<Product.ProductSupplier> suppliers,
            List<Product.ProductImage> images,
            List<Product.ProductPromotion> promotions,
            List<Product.ProductPriceEntry> priceEntries,
            UUID modelId,
            UUID brandId,
            UUID groupId,
            UUID categoryId,
            BigDecimal minStock,
            BigDecimal maxStock) {

        var now = OffsetDateTime.now();
        var costPrice = BigDecimal.ONE;
        var profitMargin = BigDecimal.ONE;
        var taxType = "IVA_19";
        return new Product(
                UUID.randomUUID(),
                "P-001",
                "Producto test",
                "770000000001",
                "REF-001",
                "Producto para pruebas",
                null,
                null,
                brandId,
                modelId,
                categoryId,
                groupId,
                null,
                costPrice,
                profitMargin,
                taxType,
                Product.calculateSalePrice(costPrice, profitMargin, taxType),
                "PEPS",
                BigDecimal.ZERO,
                minStock,
                maxStock,
                BigDecimal.ZERO,
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
                true,
                0,
                now,
                now,
                warehouses,
                suppliers,
                images,
                promotions,
                priceEntries,
                List.of()
        );
    }
}
