package co.posinvent.application.usecase;

import co.posinvent.application.dto.ProductRequest;
import co.posinvent.domain.exception.BusinessException;
import co.posinvent.domain.model.Product;
import co.posinvent.domain.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ProductUseCaseTest {

    @Mock
    private ProductRepository productRepository;

    private ProductUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new ProductUseCase(productRepository);
    }

    @Test
    void create_rejectsDuplicateWarehouseIdsBeforePersistence() {
        var warehouseId = UUID.randomUUID();

        assertCreateFails(
                request(
                        List.of(
                                new ProductRequest.ProductWarehouseRequest(null, warehouseId, null, null, true),
                                new ProductRequest.ProductWarehouseRequest(null, warehouseId, null, null, false)
                        ),
                        List.of(),
                        List.of(),
                        List.of()
                ),
                "DUPLICATE_PRODUCT_WAREHOUSE"
        );
    }

    @Test
    void create_rejectsMultipleDefaultWarehousesBeforePersistence() {
        assertCreateFails(
                request(
                        List.of(
                                new ProductRequest.ProductWarehouseRequest(null, UUID.randomUUID(), null, null, true),
                                new ProductRequest.ProductWarehouseRequest(null, UUID.randomUUID(), null, null, true)
                        ),
                        List.of(),
                        List.of(),
                        List.of()
                ),
                "MULTIPLE_DEFAULT_PRODUCT_WAREHOUSES"
        );
    }

    @Test
    void create_rejectsDuplicateSupplierIdsBeforePersistence() {
        var supplierId = UUID.randomUUID();

        assertCreateFails(
                request(
                        List.of(),
                        List.of(
                                new ProductRequest.ProductSupplierRequest(null, supplierId, "SUP-1", BigDecimal.ONE, true),
                                new ProductRequest.ProductSupplierRequest(null, supplierId, "SUP-2", BigDecimal.TEN, false)
                        ),
                        List.of(),
                        List.of()
                ),
                "DUPLICATE_PRODUCT_SUPPLIER"
        );
    }

    @Test
    void create_rejectsMultipleMainSuppliersBeforePersistence() {
        assertCreateFails(
                request(
                        List.of(),
                        List.of(
                                new ProductRequest.ProductSupplierRequest(null, UUID.randomUUID(), "SUP-1", BigDecimal.ONE, true),
                                new ProductRequest.ProductSupplierRequest(null, UUID.randomUUID(), "SUP-2", BigDecimal.TEN, true)
                        ),
                        List.of(),
                        List.of()
                ),
                "MULTIPLE_MAIN_PRODUCT_SUPPLIERS"
        );
    }

    @Test
    void create_rejectsDuplicatePriceListsBeforePersistence() {
        var priceListId = UUID.randomUUID();

        assertCreateFails(
                request(
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(
                                new ProductRequest.ProductPriceEntryRequest(null, priceListId, new BigDecimal("1.4280"), new BigDecimal("20.00")),
                                new ProductRequest.ProductPriceEntryRequest(null, priceListId, new BigDecimal("1.4399"), new BigDecimal("21.00"))
                        )
                ),
                "DUPLICATE_PRODUCT_PRICE_ENTRY"
        );
    }

    @Test
    void create_rejectsInvalidPromotionRangeBeforePersistence() {
        assertCreateFails(
                request(
                        List.of(),
                        List.of(),
                        List.of(
                                new ProductRequest.ProductPromotionRequest(
                                        null,
                                        "Promo invalid",
                                        new BigDecimal("10.00"),
                                        LocalDate.of(2026, 5, 10),
                                        LocalDate.of(2026, 5, 1),
                                        true
                                )
                        ),
                        List.of()
                ),
                "INVALID_PRODUCT_PROMOTION_DATE_RANGE"
        );
    }

    @Test
    void create_rejectsModelWithoutBrandBeforePersistence() {
        var request = new ProductRequest(
                "P-001",
                "Producto test",
                null,
                null,
                null,
                null,
                null,
                null,
                UUID.randomUUID(),
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
                List.of(),
                List.of(),
                List.of(),
                List.of()
        );

        assertCreateFails(request, "INVALID_PRODUCT_MODEL");
    }

    @Test
    void create_rejectsInvalidStockRangeBeforePersistence() {
        var request = new ProductRequest(
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
                new BigDecimal("10.00"),
                new BigDecimal("5.00"),
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
                List.of(),
                List.of(),
                List.of(),
                List.of()
        );

        assertCreateFails(request, "INVALID_STOCK_RANGE");
    }

    @Test
    void create_calculatesSalePriceBeforePersistence() {
        doAnswer(invocation -> invocation.getArgument(0, Product.class))
                .when(productRepository)
                .save(any(Product.class));

        useCase.create(request(List.of(), List.of(), List.of(), List.of()));

        var captor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(captor.capture());
        assertThat(captor.getValue().salePrice()).isEqualByComparingTo("1.3090");
    }

    @Test
    void create_appliesBackendDefaultsBeforePersistence() {
        doAnswer(invocation -> invocation.getArgument(0, Product.class))
                .when(productRepository)
                .save(any(Product.class));

        var request = new ProductRequest(
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
                new BigDecimal("100.00"),
                null,
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
                0,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of()
        );

        useCase.create(request);

        var captor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(captor.capture());
        var saved = captor.getValue();

        assertThat(saved.profitMargin()).isEqualByComparingTo("0");
        assertThat(saved.taxType()).isEqualTo("EXENTO");
        assertThat(saved.costingMethod()).isEqualTo("PROMEDIO_PONDERADO");
        assertThat(saved.initialStock()).isEqualByComparingTo("0");
        assertThat(saved.minStock()).isEqualByComparingTo("0");
        assertThat(saved.maxStock()).isEqualByComparingTo("0");
        assertThat(saved.salePrice()).isEqualByComparingTo("100.0000");
    }

    private void assertCreateFails(ProductRequest request, String expectedErrorCode) {
        assertThatThrownBy(() -> useCase.create(request))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(expectedErrorCode));

        verify(productRepository, never()).save(any());
    }

    private ProductRequest request(
            List<ProductRequest.ProductWarehouseRequest> warehouses,
            List<ProductRequest.ProductSupplierRequest> suppliers,
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
                warehouses,
                suppliers,
                List.of(),
                promotions,
                priceEntries
        );
    }
}
