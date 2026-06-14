package co.posinvent.application.usecase;

import co.posinvent.application.annotation.Auditable;
import co.posinvent.application.dto.PageResponse;
import co.posinvent.application.dto.ProductRequest;
import co.posinvent.application.dto.ProductRequest.ProductImageRequest;
import co.posinvent.application.dto.ProductRequest.ProductPriceEntryRequest;
import co.posinvent.application.dto.ProductRequest.ProductPromotionRequest;
import co.posinvent.application.dto.ProductRequest.ProductSupplierRequest;
import co.posinvent.application.dto.ProductRequest.ProductWarehouseRequest;
import co.posinvent.application.dto.ProductResponse;
import co.posinvent.domain.exception.BusinessException;
import co.posinvent.domain.exception.ResourceNotFoundException;
import co.posinvent.domain.model.Product;
import co.posinvent.domain.model.Product.ProductImage;
import co.posinvent.domain.model.Product.ProductPriceEntry;
import co.posinvent.domain.model.Product.ProductPromotion;
import co.posinvent.domain.model.Product.ProductSupplier;
import co.posinvent.domain.model.Product.ProductWarehouse;
import co.posinvent.domain.repository.ProductRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
public class ProductUseCase {

    private final ProductRepository productRepository;

    public ProductUseCase(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    // -------------------------------------------------------------------------
    // Sub-table list builders
    // -------------------------------------------------------------------------

    private List<ProductWarehouse> buildWarehouses(List<ProductWarehouseRequest> reqs) {
        if (reqs == null) return List.of();
        return reqs.stream()
                .map(r -> new ProductWarehouse(r.id(), r.warehouseId(), r.locationId(), r.unitOfMeasureId(), r.isDefault()))
                .toList();
    }

    private List<ProductSupplier> buildSuppliers(List<ProductSupplierRequest> reqs) {
        if (reqs == null) return List.of();
        return reqs.stream()
                .map(r -> new ProductSupplier(r.id(), r.supplierId(), r.supplierReference(), r.unitCost(), r.isMain()))
                .toList();
    }

    private List<ProductImage> buildImages(List<ProductImageRequest> reqs) {
        if (reqs == null) return List.of();
        return reqs.stream()
                .map(r -> new ProductImage(r.id(), r.imageUrl(), r.displayOrder()))
                .toList();
    }

    private List<ProductPromotion> buildPromotions(List<ProductPromotionRequest> reqs) {
        if (reqs == null) return List.of();
        return reqs.stream()
                .map(r -> new ProductPromotion(r.id(), r.name(), r.discountPct(), r.startDate(), r.endDate(), r.isActive()))
                .toList();
    }

    private List<ProductPriceEntry> buildPriceEntries(List<ProductPriceEntryRequest> reqs) {
        if (reqs == null) return List.of();
        return reqs.stream()
                .map(r -> new ProductPriceEntry(r.id(), r.priceListId(), r.price(), r.profitMargin()))
                .toList();
    }

    // -------------------------------------------------------------------------
    // Use cases
    // -------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> list(Pageable pageable) {
        return PageResponse.from(productRepository.findAll(pageable), ProductResponse::from);
    }

    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> searchByName(String name, Pageable pageable) {
        return PageResponse.from(productRepository.searchByName(name, pageable), ProductResponse::from);
    }

    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> searchByBarcode(String barcode, Pageable pageable) {
        return PageResponse.from(productRepository.searchByBarcode(barcode, pageable), ProductResponse::from);
    }

    @Transactional(readOnly = true)
    public ProductResponse getById(UUID id) {
        return productRepository.findById(id)
                .map(ProductResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Producto", id));
    }

    @Auditable(entityType = "PRODUCT", action = "CREATE")
    @Transactional
    public ProductResponse create(ProductRequest request) {
        if (productRepository.existsByProductCode(request.productCode())) {
            throw new BusinessException("DUPLICATE_PRODUCT_CODE",
                    "Ya existe un producto con el código: " + request.productCode());
        }

        BigDecimal salePrice = Product.calculateSalePrice(request.costPrice(), request.profitMargin(), request.taxType());

        var product = new Product(
                null,
                request.productCode(),
                request.name(),
                request.barcode(),
                request.reference(),
                request.description(),
                request.productTypeId(),
                request.productStateId(),
                request.brandId(),
                request.modelId(),
                request.categoryId(),
                request.groupId(),
                request.unitOfMeasureId(),
                request.costPrice(),
                request.profitMargin(),
                request.taxType(),
                salePrice,
                request.costingMethod(),
                request.initialStock(),
                request.minStock(),
                request.maxStock(),
                BigDecimal.ZERO,
                request.manufacturedInHouse(),
                request.costAffectingExp(),
                request.manageLots(),
                request.perishable(),
                request.belongsToProduct(),
                request.sellBelowMin(),
                request.inventoriable(),
                request.serialNumber(),
                request.originCountry(),
                request.specifications(),
                request.incomeAccountId(),
                request.inventoryAccountId(),
                request.costOfSalesAcctId(),
                request.accountingTemplateId(),
                true,
                0,
                null,
                null,
                buildWarehouses(request.warehouses()),
                buildSuppliers(request.suppliers()),
                buildImages(request.images()),
                buildPromotions(request.promotions()),
                buildPriceEntries(request.priceEntries()),
                List.of()
        );

        product.validate();

        return ProductResponse.from(productRepository.save(product));
    }

    @Auditable(entityType = "PRODUCT", action = "UPDATE")
    @Transactional
    public ProductResponse update(UUID id, ProductRequest request) {
        var existing = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Producto", id));

        if (existing.version() != request.version()) {
            throw new BusinessException("CONFLICT",
                    "El producto fue modificado por otro usuario. Por favor recargue.");
        }

        if (productRepository.existsByProductCodeAndIdNot(request.productCode(), id)) {
            throw new BusinessException("DUPLICATE_PRODUCT_CODE",
                    "Ya existe un producto con el código: " + request.productCode());
        }

        BigDecimal salePrice = Product.calculateSalePrice(request.costPrice(), request.profitMargin(), request.taxType());

        var updated = new Product(
                existing.id(),
                request.productCode(),
                request.name(),
                request.barcode(),
                request.reference(),
                request.description(),
                request.productTypeId(),
                request.productStateId(),
                request.brandId(),
                request.modelId(),
                request.categoryId(),
                request.groupId(),
                request.unitOfMeasureId(),
                request.costPrice(),
                request.profitMargin(),
                request.taxType(),
                salePrice,
                request.costingMethod(),
                request.initialStock(),
                request.minStock(),
                request.maxStock(),
                existing.totalStock(),
                request.manufacturedInHouse(),
                request.costAffectingExp(),
                request.manageLots(),
                request.perishable(),
                request.belongsToProduct(),
                request.sellBelowMin(),
                request.inventoriable(),
                request.serialNumber(),
                request.originCountry(),
                request.specifications(),
                request.incomeAccountId(),
                request.inventoryAccountId(),
                request.costOfSalesAcctId(),
                request.accountingTemplateId(),
                existing.active(),
                existing.version(),
                existing.createdAt(),
                null,
                buildWarehouses(request.warehouses()),
                buildSuppliers(request.suppliers()),
                buildImages(request.images()),
                buildPromotions(request.promotions()),
                buildPriceEntries(request.priceEntries()),
                List.of()
        );

        updated.validate();

        return ProductResponse.from(productRepository.save(updated));
    }

    @Auditable(entityType = "PRODUCT", action = "DELETE")
    @Transactional
    public void deactivate(UUID id) {
        var existing = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Producto", id));

        var deactivated = new Product(
                existing.id(),
                existing.productCode(),
                existing.name(),
                existing.barcode(),
                existing.reference(),
                existing.description(),
                existing.productTypeId(),
                existing.productStateId(),
                existing.brandId(),
                existing.modelId(),
                existing.categoryId(),
                existing.groupId(),
                existing.unitOfMeasureId(),
                existing.costPrice(),
                existing.profitMargin(),
                existing.taxType(),
                existing.salePrice(),
                existing.costingMethod(),
                existing.initialStock(),
                existing.minStock(),
                existing.maxStock(),
                existing.totalStock(),
                existing.manufacturedInHouse(),
                existing.costAffectingExp(),
                existing.manageLots(),
                existing.perishable(),
                existing.belongsToProduct(),
                existing.sellBelowMin(),
                existing.inventoriable(),
                existing.serialNumber(),
                existing.originCountry(),
                existing.specifications(),
                existing.incomeAccountId(),
                existing.inventoryAccountId(),
                existing.costOfSalesAcctId(),
                existing.accountingTemplateId(),
                false,
                existing.version(),
                existing.createdAt(),
                null,
                existing.warehouses(),
                existing.suppliers(),
                existing.images(),
                existing.promotions(),
                existing.priceEntries(),
                List.of()
        );

        productRepository.save(deactivated);
    }
}
