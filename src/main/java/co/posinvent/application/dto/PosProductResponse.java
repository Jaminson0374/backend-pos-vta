package co.posinvent.application.dto;

import co.posinvent.domain.model.Product;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Sale-facing projection of a product for the POS search. It deliberately omits cost,
 * margin, supplier, price-list and accounting data.
 */
public record PosProductResponse(
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

        String taxType,
        BigDecimal salePrice,
        BigDecimal minStock,
        BigDecimal totalStock,
        boolean active,

        List<ProductResponse.ProductImageResponse> images,
        List<ProductPresentationResponse> presentations,
        List<ProductResponse.ProductPromotionResponse> promotions
) {

    public static PosProductResponse from(Product p) {
        return new PosProductResponse(
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
                p.taxType(),
                p.salePrice(),
                p.minStock(),
                p.totalStock(),
                p.active(),
                p.images() == null ? List.of() : p.images().stream()
                        .map(i -> new ProductResponse.ProductImageResponse(
                                i.id(), i.imageUrl(), i.displayOrder()))
                        .toList(),
                p.presentations() == null ? List.of() : p.presentations().stream()
                        .map(pp -> new ProductPresentationResponse(
                                pp.id(), pp.productId(), pp.code(), pp.name(),
                                pp.unitOfMeasureId(), "", pp.conversionFactor(),
                                pp.salePrice(), pp.isDefault(), pp.active(),
                                pp.createdAt(), pp.updatedAt()))
                        .toList(),
                p.promotions() == null ? List.of() : p.promotions().stream()
                        .map(pr -> new ProductResponse.ProductPromotionResponse(
                                pr.id(), pr.name(), pr.discountPct(), pr.startDate(), pr.endDate(), pr.isActive()))
                        .toList()
        );
    }
}
