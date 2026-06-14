package co.posinvent.application.dto;

import co.posinvent.domain.model.Product;
import co.posinvent.domain.model.ProductPresentation;
import co.posinvent.domain.model.ProductFormula;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record ProductResponse(
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

        List<ProductWarehouseResponse> warehouses,
        List<ProductSupplierResponse> suppliers,
        List<ProductImageResponse> images,
        List<ProductPromotionResponse> promotions,
        List<ProductPriceEntryResponse> priceEntries,
        List<ProductPresentationResponse> presentations,
        List<ProductFormulaResponse> formulas
) {

    public static ProductResponse from(Product p) {
        return new ProductResponse(
                p.id(),
                p.productCode(),
                p.name(),
                p.barcode(),
                p.reference(),
                p.description(),
                p.productTypeId(),
                p.productStateId(),
                p.brandId(),
                p.modelId(),
                p.categoryId(),
                p.groupId(),
                p.unitOfMeasureId(),
                p.costPrice(),
                p.profitMargin(),
                p.taxType(),
                p.salePrice(),
                p.costingMethod(),
                p.initialStock(),
                p.minStock(),
                p.maxStock(),
                p.totalStock(),
                p.manufacturedInHouse(),
                p.costAffectingExp(),
                p.manageLots(),
                p.perishable(),
                p.belongsToProduct(),
                p.sellBelowMin(),
                p.inventoriable(),
                p.serialNumber(),
                p.originCountry(),
                p.specifications(),
                p.incomeAccountId(),
                p.inventoryAccountId(),
                p.costOfSalesAcctId(),
                p.accountingTemplateId(),
                p.active(),
                p.version(),
                p.createdAt(),
                p.updatedAt(),
                p.warehouses() == null ? List.of() : p.warehouses().stream()
                        .map(w -> new ProductWarehouseResponse(
                                w.id(), w.warehouseId(), w.locationId(), w.unitOfMeasureId(), w.isDefault()))
                        .toList(),
                p.suppliers() == null ? List.of() : p.suppliers().stream()
                        .map(s -> new ProductSupplierResponse(
                                s.id(), s.supplierId(), s.supplierReference(), s.unitCost(), s.isMain()))
                        .toList(),
                p.images() == null ? List.of() : p.images().stream()
                        .map(i -> new ProductImageResponse(i.id(), i.imageUrl(), i.displayOrder()))
                        .toList(),
                p.promotions() == null ? List.of() : p.promotions().stream()
                        .map(pr -> new ProductPromotionResponse(
                                pr.id(), pr.name(), pr.discountPct(), pr.startDate(), pr.endDate(), pr.isActive()))
                        .toList(),
                p.priceEntries() == null ? List.of() : p.priceEntries().stream()
                        .map(pe -> new ProductPriceEntryResponse(
                                pe.id(), pe.priceListId(), pe.price(), pe.profitMargin()))
                        .toList(),
                p.presentations() == null ? List.of() : p.presentations().stream()
                        .map(pp -> new ProductPresentationResponse(
                                pp.id(), pp.productId(), pp.code(), pp.name(),
                                pp.unitOfMeasureId(), "", pp.conversionFactor(),
                                pp.salePrice(), pp.isDefault(), pp.active(),
                                pp.createdAt(), pp.updatedAt()))
                        .toList(),
                List.of()
        );
    }

    public record ProductWarehouseResponse(
            UUID id,
            UUID warehouseId,
            UUID locationId,
            UUID unitOfMeasureId,
            boolean isDefault
    ) {}

    public record ProductSupplierResponse(
            UUID id,
            UUID supplierId,
            String supplierReference,
            BigDecimal unitCost,
            boolean isMain
    ) {}

    public record ProductImageResponse(
            UUID id,
            String imageUrl,
            int displayOrder
    ) {}

    public record ProductPromotionResponse(
            UUID id,
            String name,
            BigDecimal discountPct,
            LocalDate startDate,
            LocalDate endDate,
            boolean isActive
    ) {}

    public record ProductPriceEntryResponse(
            UUID id,
            UUID priceListId,
            BigDecimal price,
            BigDecimal profitMargin
    ) {}

    public record ProductFormulaResponse(
            UUID id,
            UUID parentProductId,
            UUID componentProductId,
            BigDecimal quantity,
            UUID unitOfMeasureId,
            int sequenceNumber,
            String notes,
            boolean active,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {
        public static ProductFormulaResponse from(ProductFormula f) {
            return new ProductFormulaResponse(
                    f.id(), f.parentProductId(), f.componentProductId(),
                    f.quantity(), f.unitOfMeasureId(), f.sequenceNumber(),
                    f.notes(), f.active(), f.createdAt(), f.updatedAt()
            );
        }
    }
}
