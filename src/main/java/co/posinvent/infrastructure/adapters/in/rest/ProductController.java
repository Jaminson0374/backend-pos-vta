package co.posinvent.infrastructure.adapters.in.rest;

import co.posinvent.application.dto.PageResponse;
import co.posinvent.application.dto.ProductRequest;
import co.posinvent.application.dto.ProductResponse;
import co.posinvent.application.usecase.ProductUseCase;
import co.posinvent.domain.repository.ProductFormulaRepository;
import co.posinvent.domain.model.ProductFormula;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/products")
public class ProductController {

    private final ProductUseCase productUseCase;
    private final ProductFormulaRepository formulaRepo;

    public ProductController(ProductUseCase productUseCase, ProductFormulaRepository formulaRepo) {
        this.productUseCase = productUseCase;
        this.formulaRepo = formulaRepo;
    }

    @GetMapping
    public ResponseEntity<PageResponse<ProductResponse>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "name") String searchType
    ) {
        var pageable = PageRequest.of(page, size);

        PageResponse<ProductResponse> result;
        if (search == null || search.isBlank()) {
            result = productUseCase.list(pageable);
        } else if ("barcode".equalsIgnoreCase(searchType)) {
            result = productUseCase.searchByBarcode(search, pageable);
        } else {
            result = productUseCase.searchByName(search, pageable);
        }

        return ResponseEntity.ok(result);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProductResponse> getById(@PathVariable UUID id) {
        var product = productUseCase.getById(id);
        var formulas = formulaRepo.findByParentProductId(id).stream()
                .map(ProductResponse.ProductFormulaResponse::from)
                .toList();
        var enriched = new ProductResponse(
                product.id(), product.productCode(), product.name(), product.barcode(),
                product.reference(), product.description(), product.productTypeId(),
                product.productStateId(), product.brandId(), product.modelId(),
                product.categoryId(), product.groupId(), product.unitOfMeasureId(),
                product.costPrice(), product.profitMargin(), product.taxType(),
                product.salePrice(), product.costingMethod(), product.initialStock(),
                product.minStock(), product.maxStock(), product.totalStock(),
                product.manufacturedInHouse(), product.costAffectingExp(), product.manageLots(),
                product.perishable(), product.belongsToProduct(), product.sellBelowMin(),
                product.inventoriable(), product.serialNumber(), product.originCountry(),
                product.specifications(), product.incomeAccountId(), product.inventoryAccountId(),
                product.costOfSalesAcctId(), product.accountingTemplateId(), product.active(), product.version(),
                product.createdAt(), product.updatedAt(),
                product.warehouses(), product.suppliers(), product.images(),
                product.promotions(), product.priceEntries(), product.presentations(),
                formulas
        );
        return ResponseEntity.ok(enriched);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ProductResponse> create(@Valid @RequestBody ProductRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(productUseCase.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ProductResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody ProductRequest request
    ) {
        return ResponseEntity.ok(productUseCase.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deactivate(@PathVariable UUID id) {
        productUseCase.deactivate(id);
        return ResponseEntity.noContent().build();
    }
}
