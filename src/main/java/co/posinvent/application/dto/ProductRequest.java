package co.posinvent.application.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record ProductRequest(
        @NotBlank @Size(max = 50)  String productCode,
        @NotBlank @Size(max = 200) String name,
        @Size(max = 100) String barcode,
        @Size(max = 100) String reference,
        String description,

        UUID productTypeId,
        UUID productStateId,
        UUID brandId,
        UUID modelId,
        UUID categoryId,
        UUID groupId,
        UUID unitOfMeasureId,

        @NotNull @DecimalMin("0") BigDecimal costPrice,
        @DecimalMin("0") @DecimalMax("100") BigDecimal profitMargin,

        @Pattern(regexp = "EXENTO|IVA_5|IVA_8|IVA_19",
                 message = "taxType debe ser EXENTO, IVA_5, IVA_8 o IVA_19")
        String taxType,

        @Pattern(regexp = "PEPS|PROMEDIO_PONDERADO|ESTANDAR|IDENTIFICACION_ESPECIFICA|YIELD_COSTING",
                 message = "costingMethod debe ser PEPS, PROMEDIO_PONDERADO, ESTANDAR, IDENTIFICACION_ESPECIFICA o YIELD_COSTING")
        String costingMethod,

        @DecimalMin("0") BigDecimal initialStock,
        @DecimalMin("0") BigDecimal minStock,
        @DecimalMin("0") BigDecimal maxStock,

        boolean manufacturedInHouse,
        boolean costAffectingExp,
        boolean manageLots,
        boolean perishable,
        boolean belongsToProduct,
        boolean sellBelowMin,
        boolean inventoriable,

        @Size(max = 100) String serialNumber,
        @Size(max = 100) String originCountry,
        String specifications,

        UUID incomeAccountId,
        UUID inventoryAccountId,
        UUID costOfSalesAcctId,
        UUID accountingTemplateId,

        int version,

        @Valid List<ProductWarehouseRequest> warehouses,
        @Valid List<ProductSupplierRequest> suppliers,
        @Valid List<ProductImageRequest> images,
        @Valid List<ProductPromotionRequest> promotions,
        @Valid List<ProductPriceEntryRequest> priceEntries
) {

    public record ProductWarehouseRequest(
            UUID id,
            UUID warehouseId,
            UUID locationId,
            UUID unitOfMeasureId,
            boolean isDefault
    ) {}

    public record ProductSupplierRequest(
            UUID id,
            UUID supplierId,
            @Size(max = 100) String supplierReference,
            @DecimalMin("0") BigDecimal unitCost,
            boolean isMain
    ) {}

    public record ProductImageRequest(
            UUID id,
            @Size(max = 500) String imageUrl,
            @Min(0) @Max(3) int displayOrder
    ) {}

    public record ProductPromotionRequest(
            UUID id,
            @Size(max = 200) String name,
            @DecimalMin("0") @DecimalMax("100") BigDecimal discountPct,
            LocalDate startDate,
            LocalDate endDate,
            boolean isActive
    ) {}

    public record ProductPriceEntryRequest(
            UUID id,
            UUID priceListId,
            @DecimalMin("0") BigDecimal price,
            @DecimalMin("0") @DecimalMax("100") BigDecimal profitMargin
    ) {}
}
