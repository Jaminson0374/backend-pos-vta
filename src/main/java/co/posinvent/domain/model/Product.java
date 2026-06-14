package co.posinvent.domain.model;

import co.posinvent.domain.exception.BusinessException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public record Product(
        UUID id,
        String productCode,
        String name,
        String barcode,
        String reference,
        String description,
        UUID productTypeId,
        UUID productStateId,
        UUID brandId,
        UUID modelId,
        UUID categoryId,
        UUID groupId,
        UUID unitOfMeasureId,
        BigDecimal costPrice,
        BigDecimal profitMargin,
        String taxType,
        BigDecimal salePrice,
        String costingMethod,
        BigDecimal initialStock,
        BigDecimal minStock,
        BigDecimal maxStock,
        BigDecimal totalStock,
        boolean manufacturedInHouse,
        boolean costAffectingExp,
        boolean manageLots,
        boolean perishable,
        boolean belongsToProduct,
        boolean sellBelowMin,
        boolean inventoriable,
        String serialNumber,
        String originCountry,
        String specifications,
        UUID incomeAccountId,
        UUID inventoryAccountId,
        UUID costOfSalesAcctId,
        UUID accountingTemplateId,
        boolean active,
        int version,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        List<ProductWarehouse> warehouses,
        List<ProductSupplier> suppliers,
        List<ProductImage> images,
        List<ProductPromotion> promotions,
        List<ProductPriceEntry> priceEntries,
        List<ProductPresentation> presentations
) {

    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final BigDecimal DEFAULT_DECIMAL = BigDecimal.ZERO;
    private static final String DEFAULT_TAX_TYPE = "EXENTO";
    private static final String DEFAULT_COSTING_METHOD = "PROMEDIO_PONDERADO";

    public Product {
        profitMargin = defaultDecimal(profitMargin);
        taxType = defaultString(taxType, DEFAULT_TAX_TYPE);
        costingMethod = defaultString(costingMethod, DEFAULT_COSTING_METHOD);
        initialStock = defaultDecimal(initialStock);
        minStock = defaultDecimal(minStock);
        maxStock = defaultDecimal(maxStock);
        totalStock = defaultDecimal(totalStock);
    }

    public void validate() {
        validateRequiredParent(modelId, brandId,
                "INVALID_PRODUCT_MODEL", "El modelo requiere una marca asociada.");
        validateRequiredParent(groupId, categoryId,
                "INVALID_PRODUCT_GROUP", "El grupo requiere una categoria asociada.");
        validateSalePriceConsistency();
        validateStockRange();
        validateWarehouses();
        validateSuppliers();
        validateImages();
        validatePromotions();
        validatePriceEntries();
    }

    public static BigDecimal calculateSalePrice(BigDecimal costPrice, BigDecimal profitMargin, String taxType) {
        if (costPrice == null) {
            return BigDecimal.ZERO;
        }

        BigDecimal margin = defaultDecimal(profitMargin);

        int ivaRate = switch (defaultString(taxType, DEFAULT_TAX_TYPE)) {
            case "IVA_5" -> 5;
            case "IVA_8" -> 8;
            case "IVA_19" -> 19;
            default -> 0;
        };

        BigDecimal withMargin = costPrice.multiply(
                BigDecimal.ONE.add(margin.divide(HUNDRED, 10, RoundingMode.HALF_UP)));
        BigDecimal withTax = withMargin.multiply(
                BigDecimal.ONE.add(new BigDecimal(ivaRate).divide(HUNDRED, 10, RoundingMode.HALF_UP)));

        return withTax.setScale(4, RoundingMode.HALF_UP);
    }

    public record ProductWarehouse(
            UUID id,
            UUID warehouseId,
            UUID locationId,
            UUID unitOfMeasureId,
            boolean isDefault
    ) {}

    public record ProductSupplier(
            UUID id,
            UUID supplierId,
            String supplierReference,
            BigDecimal unitCost,
            boolean isMain
    ) {}

    public record ProductImage(
            UUID id,
            String imageUrl,
            int displayOrder
    ) {}

    public record ProductPromotion(
            UUID id,
            String name,
            BigDecimal discountPct,
            LocalDate startDate,
            LocalDate endDate,
            boolean isActive
    ) {}

    public record ProductPriceEntry(
            UUID id,
            UUID priceListId,
            BigDecimal price,
            BigDecimal profitMargin
    ) {}

    private void validateWarehouses() {
        var seenWarehouseIds = new HashSet<UUID>();
        var defaultCount = 0;

        for (var warehouse : safeList(warehouses)) {
            if (warehouse.warehouseId() == null) {
                throw new BusinessException("INVALID_PRODUCT_WAREHOUSE",
                        "Cada bodega del producto debe tener warehouseId.");
            }
            if (!seenWarehouseIds.add(warehouse.warehouseId())) {
                throw new BusinessException("DUPLICATE_PRODUCT_WAREHOUSE",
                        "El producto no puede repetir la misma bodega.");
            }
            if (warehouse.isDefault()) {
                defaultCount++;
            }
        }

        if (defaultCount > 1) {
            throw new BusinessException("MULTIPLE_DEFAULT_PRODUCT_WAREHOUSES",
                    "El producto solo puede tener una bodega predeterminada.");
        }
    }

    private void validateRequiredParent(UUID childId, UUID parentId, String errorCode, String message) {
        if (childId != null && parentId == null) {
            throw new BusinessException(errorCode, message);
        }
    }

    private void validateStockRange() {
        if (minStock != null && maxStock != null && maxStock.compareTo(minStock) < 0) {
            throw new BusinessException("INVALID_STOCK_RANGE",
                    "El stock maximo no puede ser menor al stock minimo.");
        }
    }

    private void validateSalePriceConsistency() {
        if (salePrice == null) {
            throw new BusinessException("INVALID_SALE_PRICE",
                    "El producto debe tener un precio de venta calculado.");
        }

        validateCalculatedPrice(
                salePrice,
                profitMargin,
                "INCONSISTENT_SALE_PRICE",
                "El precio de venta no coincide con el costo, margen e impuesto del producto.");
    }

    private void validateSuppliers() {
        var seenSupplierIds = new HashSet<UUID>();
        var mainCount = 0;

        for (var supplier : safeList(suppliers)) {
            if (supplier.supplierId() == null) {
                throw new BusinessException("INVALID_PRODUCT_SUPPLIER",
                        "Cada proveedor del producto debe tener supplierId.");
            }
            if (!seenSupplierIds.add(supplier.supplierId())) {
                throw new BusinessException("DUPLICATE_PRODUCT_SUPPLIER",
                        "El producto no puede repetir el mismo proveedor.");
            }
            if (supplier.isMain()) {
                mainCount++;
            }
        }

        if (mainCount > 1) {
            throw new BusinessException("MULTIPLE_MAIN_PRODUCT_SUPPLIERS",
                    "El producto solo puede tener un proveedor principal.");
        }
    }

    private void validateImages() {
        var seenDisplayOrders = new HashSet<Integer>();

        for (var image : safeList(images)) {
            if (image.imageUrl() == null || image.imageUrl().isBlank()) {
                throw new BusinessException("INVALID_PRODUCT_IMAGE",
                        "Cada imagen del producto debe tener imageUrl.");
            }
            if (!seenDisplayOrders.add(image.displayOrder())) {
                throw new BusinessException("DUPLICATE_PRODUCT_IMAGE_DISPLAY_ORDER",
                        "El producto no puede repetir el orden de imagen.");
            }
        }
    }

    private void validatePromotions() {
        Set<PromotionIdentity> identities = new HashSet<>();

        for (var promotion : safeList(promotions)) {
            if (promotion.name() == null || promotion.name().isBlank()) {
                throw new BusinessException("INVALID_PRODUCT_PROMOTION",
                        "Cada promocion del producto debe tener nombre.");
            }
            if (promotion.discountPct() == null || promotion.startDate() == null || promotion.endDate() == null) {
                throw new BusinessException("INVALID_PRODUCT_PROMOTION",
                        "Cada promocion del producto debe tener descuento y rango de fechas.");
            }
            if (promotion.startDate().isAfter(promotion.endDate())) {
                throw new BusinessException("INVALID_PRODUCT_PROMOTION_DATE_RANGE",
                        "La fecha inicial de la promocion no puede ser mayor a la fecha final.");
            }

            var identity = new PromotionIdentity(
                    promotion.name(),
                    promotion.discountPct(),
                    promotion.startDate(),
                    promotion.endDate(),
                    promotion.isActive());

            if (!identities.add(identity)) {
                throw new BusinessException("DUPLICATE_PRODUCT_PROMOTION",
                        "El producto no puede repetir la misma promocion.");
            }
        }
    }

    private void validatePriceEntries() {
        var seenPriceListIds = new HashSet<UUID>();

        for (var priceEntry : safeList(priceEntries)) {
            if (priceEntry.priceListId() == null) {
                throw new BusinessException("INVALID_PRODUCT_PRICE_ENTRY",
                        "Cada precio del producto debe tener priceListId.");
            }
            if (priceEntry.price() == null || priceEntry.profitMargin() == null) {
                throw new BusinessException("INVALID_PRODUCT_PRICE_ENTRY",
                        "Cada precio del producto debe tener precio y margen.");
            }
            validateCalculatedPrice(
                    priceEntry.price(),
                    priceEntry.profitMargin(),
                    "INCONSISTENT_PRODUCT_PRICE_ENTRY",
                    "Cada precio por lista debe coincidir con el costo, margen e impuesto del producto.");
            if (!seenPriceListIds.add(priceEntry.priceListId())) {
                throw new BusinessException("DUPLICATE_PRODUCT_PRICE_ENTRY",
                        "El producto no puede repetir la misma lista de precios.");
            }
        }
    }

    private void validateCalculatedPrice(
            BigDecimal price,
            BigDecimal margin,
            String errorCode,
            String message) {
        var expectedPrice = calculateSalePrice(costPrice, margin, taxType);
        if (price.compareTo(expectedPrice) != 0) {
            throw new BusinessException(errorCode, message);
        }
    }

    private static <T> List<T> safeList(List<T> items) {
        return items == null ? List.of() : items;
    }

    private static BigDecimal defaultDecimal(BigDecimal value) {
        return value == null ? DEFAULT_DECIMAL : value;
    }

    private static String defaultString(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private record PromotionIdentity(
            String name,
            BigDecimal discountPct,
            LocalDate startDate,
            LocalDate endDate,
            boolean isActive
    ) {}
}
