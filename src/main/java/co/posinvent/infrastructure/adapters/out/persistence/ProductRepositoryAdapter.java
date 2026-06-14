package co.posinvent.infrastructure.adapters.out.persistence;

import co.posinvent.domain.model.Product;
import co.posinvent.domain.repository.ProductRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Repository
class ProductRepositoryAdapter implements ProductRepository {

    private final ProductJpaRepository jpa;
    private final ProductMapper mapper;
    private final ProductTypeJpaRepository productTypeJpa;
    private final ProductStateJpaRepository productStateJpa;
    private final BrandJpaRepository brandJpa;
    private final ProductModelJpaRepository modelJpa;
    private final ProductCategoryJpaRepository categoryJpa;
    private final ProductGroupJpaRepository groupJpa;
    private final UnitOfMeasureJpaRepository uomJpa;
    private final PucAccountJpaRepository pucJpa;
    private final ProductWarehouseJpaRepository warehouseJpa;
    private final ProductSupplierJpaRepository supplierJpa;
    private final ProductImageJpaRepository imageJpa;
    private final ProductPromotionJpaRepository promotionJpa;
    private final ProductPriceEntryJpaRepository priceEntryJpa;
    private final ProductPresentationJpaRepository presentationJpa;

    ProductRepositoryAdapter(
            ProductJpaRepository jpa,
            ProductMapper mapper,
            ProductTypeJpaRepository productTypeJpa,
            ProductStateJpaRepository productStateJpa,
            BrandJpaRepository brandJpa,
            ProductModelJpaRepository modelJpa,
            ProductCategoryJpaRepository categoryJpa,
            ProductGroupJpaRepository groupJpa,
            UnitOfMeasureJpaRepository uomJpa,
            PucAccountJpaRepository pucJpa,
            ProductWarehouseJpaRepository warehouseJpa,
            ProductSupplierJpaRepository supplierJpa,
            ProductImageJpaRepository imageJpa,
            ProductPromotionJpaRepository promotionJpa,
            ProductPriceEntryJpaRepository priceEntryJpa,
            ProductPresentationJpaRepository presentationJpa) {
        this.jpa = jpa;
        this.mapper = mapper;
        this.productTypeJpa = productTypeJpa;
        this.productStateJpa = productStateJpa;
        this.brandJpa = brandJpa;
        this.modelJpa = modelJpa;
        this.categoryJpa = categoryJpa;
        this.groupJpa = groupJpa;
        this.uomJpa = uomJpa;
        this.pucJpa = pucJpa;
        this.warehouseJpa = warehouseJpa;
        this.supplierJpa = supplierJpa;
        this.imageJpa = imageJpa;
        this.promotionJpa = promotionJpa;
        this.priceEntryJpa = priceEntryJpa;
        this.presentationJpa = presentationJpa;
    }

    @Override
    public Product save(Product product) {
        product.validate();
        var entity = mapper.toEntityBase(product);

        if (product.productTypeId() != null) {
            entity.setProductType(productTypeJpa.getReferenceById(product.productTypeId()));
        }
        if (product.productStateId() != null) {
            entity.setProductState(productStateJpa.getReferenceById(product.productStateId()));
        }
        if (product.brandId() != null) {
            entity.setBrand(brandJpa.getReferenceById(product.brandId()));
        }
        if (product.modelId() != null) {
            entity.setModel(modelJpa.getReferenceById(product.modelId()));
        }
        if (product.categoryId() != null) {
            entity.setCategory(categoryJpa.getReferenceById(product.categoryId()));
        }
        if (product.groupId() != null) {
            entity.setProductGroup(groupJpa.getReferenceById(product.groupId()));
        }
        if (product.unitOfMeasureId() != null) {
            entity.setUnitOfMeasure(uomJpa.getReferenceById(product.unitOfMeasureId()));
        }
        if (product.incomeAccountId() != null) {
            entity.setIncomeAccount(pucJpa.getReferenceById(product.incomeAccountId()));
        }
        if (product.inventoryAccountId() != null) {
            entity.setInventoryAccount(pucJpa.getReferenceById(product.inventoryAccountId()));
        }
        if (product.costOfSalesAcctId() != null) {
            entity.setCostOfSalesAcct(pucJpa.getReferenceById(product.costOfSalesAcctId()));
        }

        var saved = jpa.save(entity);

        saveWarehouses(saved, product.warehouses());
        saveSuppliers(saved, product.suppliers());
        saveImages(saved, product.images());
        savePromotions(saved, product.promotions());
        savePriceEntries(saved, product.priceEntries());

        return findById(saved.getId()).orElseThrow();
    }

    @Override
    public Optional<Product> findById(UUID id) {
        return jpa.findById(id).map(entity -> {
            var warehouses = warehouseJpa.findByProductId(id).stream()
                    .map(w -> new Product.ProductWarehouse(
                            w.getId(), w.getWarehouseId(), w.getLocationId(),
                            w.getUnitOfMeasureId(), w.isDefault()))
                    .toList();
            var suppliers = supplierJpa.findByProductId(id).stream()
                    .map(s -> new Product.ProductSupplier(
                            s.getId(), s.getSupplierId(), s.getSupplierReference(),
                            s.getUnitCost(), s.isMain()))
                    .toList();
            var images = imageJpa.findByProductId(id).stream()
                    .map(i -> new Product.ProductImage(
                            i.getId(), i.getImageUrl(), i.getDisplayOrder()))
                    .toList();
            var promotions = promotionJpa.findByProductId(id).stream()
                    .map(p -> new Product.ProductPromotion(
                            p.getId(), p.getName(), p.getDiscountPct(),
                            p.getStartDate(), p.getEndDate(), p.isActive()))
                    .toList();
            var priceEntries = priceEntryJpa.findByProductId(id).stream()
                    .map(pe -> new Product.ProductPriceEntry(
                            pe.getId(), pe.getPriceListId(), pe.getPrice(), pe.getProfitMargin()))
                    .toList();
            var presentations = presentationJpa.findByProductIdOrderByCode(id).stream()
                    .map(p -> new co.posinvent.domain.model.ProductPresentation(
                            p.getId(), p.getProductId(), p.getCode(), p.getName(),
                            p.getUnitOfMeasureId(), p.getConversionFactor(), p.getSalePrice(),
                            p.isDefault(), p.isActive(), p.getCreatedAt(), p.getUpdatedAt()))
                    .toList();
            return buildDomain(entity, warehouses, suppliers, images, promotions, priceEntries, presentations);
        });
    }

    @Override
    public Page<Product> findAll(Pageable pageable) {
        var page = jpa.findAllByOrderByNameAsc(pageable);
        return enrichWithImages(page);
    }

    @Override
    public Page<Product> searchByName(String name, Pageable pageable) {
        var page = jpa.findByNameContainingIgnoreCase(name, pageable);
        return enrichWithImages(page);
    }

    @Override
    public Page<Product> searchByBarcode(String barcode, Pageable pageable) {
        var page = jpa.findByBarcodeContaining(barcode, pageable);
        return enrichWithImages(page);
    }

    @Override
    public Optional<Product> findByProductCode(String productCode) {
        return jpa.findByProductCode(productCode)
                .map(e -> {
                    var images = imageJpa.findByProductId(e.getId()).stream()
                            .map(i -> new Product.ProductImage(
                                    i.getId(), i.getImageUrl(), i.getDisplayOrder()))
                            .toList();
                    return buildDomain(e, List.of(), List.of(), images, List.of(), List.of(), List.of());
                });
    }

    @Override
    public boolean existsByProductCode(String productCode) {
        return jpa.existsByProductCode(productCode);
    }

    @Override
    public boolean existsByProductCodeAndIdNot(String productCode, UUID id) {
        return jpa.existsByProductCodeAndIdNot(productCode, id);
    }

    // --- Private helpers ---

    private Page<Product> enrichWithImages(Page<ProductEntity> page) {
        var productIds = page.getContent().stream().map(ProductEntity::getId).toList();
        var imagesByProduct = imageJpa.findByProductIds(productIds).stream()
                .collect(Collectors.groupingBy(i -> i.getProduct().getId()));
        return page.map(e -> {
            var images = imagesByProduct.getOrDefault(e.getId(), List.of()).stream()
                    .map(i -> new Product.ProductImage(
                            i.getId(), i.getImageUrl(), i.getDisplayOrder()))
                    .toList();
            return buildDomain(e, List.of(), List.of(), images, List.of(), List.of(), List.of());
        });
    }

    private Product buildDomain(
            ProductEntity entity,
            List<Product.ProductWarehouse> warehouses,
            List<Product.ProductSupplier> suppliers,
            List<Product.ProductImage> images,
            List<Product.ProductPromotion> promotions,
            List<Product.ProductPriceEntry> priceEntries,
            List<co.posinvent.domain.model.ProductPresentation> presentations) {

        var base = mapper.toDomain(entity);
        return new Product(
                base.id(),
                base.productCode(),
                base.name(),
                base.barcode(),
                base.reference(),
                base.description(),
                base.productTypeId(),
                base.productStateId(),
                base.brandId(),
                base.modelId(),
                base.categoryId(),
                base.groupId(),
                base.unitOfMeasureId(),
                base.costPrice(),
                base.profitMargin(),
                base.taxType(),
                base.salePrice(),
                base.costingMethod(),
                base.initialStock(),
                base.minStock(),
                base.maxStock(),
                base.totalStock(),
                base.manufacturedInHouse(),
                base.costAffectingExp(),
                base.manageLots(),
                base.perishable(),
                base.belongsToProduct(),
                base.sellBelowMin(),
                base.inventoriable(),
                base.serialNumber(),
                base.originCountry(),
                base.specifications(),
                base.incomeAccountId(),
                base.inventoryAccountId(),
                base.costOfSalesAcctId(),
                base.accountingTemplateId(),
                base.active(),
                base.version(),
                base.createdAt(),
                base.updatedAt(),
                warehouses,
                suppliers,
                images,
                promotions,
                priceEntries,
                presentations
        );
    }

    private void saveWarehouses(ProductEntity saved, List<Product.ProductWarehouse> list) {
        syncChildren(
                list,
                warehouseJpa.findByProductId(saved.getId()),
                warehouseJpa,
                Product.ProductWarehouse::id,
                ProductWarehouseEntity::getId,
                Product.ProductWarehouse::warehouseId,
                ProductWarehouseEntity::getWarehouseId,
                ProductWarehouseEntity::new,
                (entity, warehouse) -> {
                    entity.setProduct(saved);
                    entity.setWarehouseId(warehouse.warehouseId());
                    entity.setLocationId(warehouse.locationId());
                    entity.setUnitOfMeasureId(warehouse.unitOfMeasureId());
                    entity.setDefault(warehouse.isDefault());
                },
                (entity, warehouse) -> Objects.equals(entity.getWarehouseId(), warehouse.warehouseId())
        );
    }

    private void saveSuppliers(ProductEntity saved, List<Product.ProductSupplier> list) {
        syncChildren(
                list,
                supplierJpa.findByProductId(saved.getId()),
                supplierJpa,
                Product.ProductSupplier::id,
                ProductSupplierEntity::getId,
                Product.ProductSupplier::supplierId,
                ProductSupplierEntity::getSupplierId,
                ProductSupplierEntity::new,
                (entity, supplier) -> {
                    entity.setProduct(saved);
                    entity.setSupplierId(supplier.supplierId());
                    entity.setSupplierReference(supplier.supplierReference());
                    entity.setUnitCost(supplier.unitCost());
                    entity.setMain(supplier.isMain());
                },
                (entity, supplier) -> Objects.equals(entity.getSupplierId(), supplier.supplierId())
        );
    }

    private void saveImages(ProductEntity saved, List<Product.ProductImage> list) {
        syncChildren(
                list,
                imageJpa.findByProductId(saved.getId()),
                imageJpa,
                Product.ProductImage::id,
                ProductImageEntity::getId,
                image -> (short) image.displayOrder(),
                ProductImageEntity::getDisplayOrder,
                ProductImageEntity::new,
                (entity, image) -> {
                    entity.setProduct(saved);
                    entity.setImageUrl(image.imageUrl());
                    entity.setDisplayOrder((short) image.displayOrder());
                },
                (entity, image) -> entity.getDisplayOrder() == (short) image.displayOrder()
        );
    }

    private void savePromotions(ProductEntity saved, List<Product.ProductPromotion> list) {
        syncChildren(
                list,
                promotionJpa.findByProductId(saved.getId()),
                promotionJpa,
                Product.ProductPromotion::id,
                ProductPromotionEntity::getId,
                this::promotionIdentity,
                this::promotionIdentity,
                ProductPromotionEntity::new,
                (entity, promotion) -> {
                    entity.setProduct(saved);
                    entity.setName(promotion.name());
                    entity.setDiscountPct(promotion.discountPct());
                    entity.setStartDate(promotion.startDate());
                    entity.setEndDate(promotion.endDate());
                    entity.setActive(promotion.isActive());
                },
                (entity, promotion) -> true
        );
    }

    private void savePriceEntries(ProductEntity saved, List<Product.ProductPriceEntry> list) {
        syncChildren(
                list,
                priceEntryJpa.findByProductId(saved.getId()),
                priceEntryJpa,
                Product.ProductPriceEntry::id,
                ProductPriceEntryEntity::getId,
                Product.ProductPriceEntry::priceListId,
                ProductPriceEntryEntity::getPriceListId,
                ProductPriceEntryEntity::new,
                (entity, priceEntry) -> {
                    entity.setProduct(saved);
                    entity.setPriceListId(priceEntry.priceListId());
                    entity.setPrice(priceEntry.price());
                    entity.setProfitMargin(priceEntry.profitMargin());
                },
                (entity, priceEntry) -> Objects.equals(entity.getPriceListId(), priceEntry.priceListId())
        );
    }

    private <T, E> void syncChildren(
            List<T> requestedItems,
            List<E> existingItems,
            JpaRepository<E, UUID> repository,
            Function<T, UUID> requestIdExtractor,
            Function<E, UUID> entityIdExtractor,
            Function<T, Object> requestIdentityExtractor,
            Function<E, Object> entityIdentityExtractor,
            Supplier<E> entityFactory,
            BiConsumer<E, T> updater,
            BiPredicate<E, T> canUpdateInPlace) {

        var requested = requestedItems == null ? List.<T>of() : requestedItems;
        if (requested.isEmpty() && existingItems.isEmpty()) {
            return;
        }

        var availableById = new HashMap<UUID, E>();
        var availableByIdentity = new LinkedHashMap<Object, E>();
        var entitiesToDelete = new LinkedHashMap<UUID, E>();

        for (var entity : existingItems) {
            var id = entityIdExtractor.apply(entity);
            if (id == null) {
                continue;
            }
            availableById.put(id, entity);
            entitiesToDelete.put(id, entity);

            var identity = entityIdentityExtractor.apply(entity);
            if (identity != null) {
                availableByIdentity.putIfAbsent(identity, entity);
            }
        }

        var entitiesToSave = new ArrayList<E>(requested.size());
        for (var item : requested) {
            var entity = findExistingEntity(item, availableById, availableByIdentity, requestIdExtractor, requestIdentityExtractor);

            if (entity != null && canUpdateInPlace.test(entity, item)) {
                detachFromAvailable(entity, availableById, availableByIdentity, entityIdExtractor, entityIdentityExtractor);
                entitiesToDelete.remove(entityIdExtractor.apply(entity));
                updater.accept(entity, item);
                entitiesToSave.add(entity);
                continue;
            }

            if (entity != null) {
                detachFromAvailable(entity, availableById, availableByIdentity, entityIdExtractor, entityIdentityExtractor);
            }

            var created = entityFactory.get();
            updater.accept(created, item);
            entitiesToSave.add(created);
        }

        if (!entitiesToDelete.isEmpty()) {
            repository.deleteAll(entitiesToDelete.values());
            repository.flush();
        }

        if (!entitiesToSave.isEmpty()) {
            repository.saveAll(entitiesToSave);
        }
    }

    private <T, E> E findExistingEntity(
            T item,
            Map<UUID, E> availableById,
            Map<Object, E> availableByIdentity,
            Function<T, UUID> requestIdExtractor,
            Function<T, Object> requestIdentityExtractor) {

        var requestedId = requestIdExtractor.apply(item);
        if (requestedId != null) {
            var byId = availableById.get(requestedId);
            if (byId != null) {
                return byId;
            }
        }

        var identity = requestIdentityExtractor.apply(item);
        if (identity == null) {
            return null;
        }

        return availableByIdentity.get(identity);
    }

    private <E> void detachFromAvailable(
            E entity,
            Map<UUID, E> availableById,
            Map<Object, E> availableByIdentity,
            Function<E, UUID> entityIdExtractor,
            Function<E, Object> entityIdentityExtractor) {

        var entityId = entityIdExtractor.apply(entity);
        if (entityId != null) {
            availableById.remove(entityId);
        }

        var identity = entityIdentityExtractor.apply(entity);
        if (identity != null && availableByIdentity.get(identity) == entity) {
            availableByIdentity.remove(identity);
        }
    }

    private String promotionIdentity(Product.ProductPromotion promotion) {
        return String.join("|",
                Objects.toString(promotion.name(), ""),
                Objects.toString(promotion.discountPct(), ""),
                Objects.toString(promotion.startDate(), ""),
                Objects.toString(promotion.endDate(), ""),
                Boolean.toString(promotion.isActive()));
    }

    private String promotionIdentity(ProductPromotionEntity promotion) {
        return String.join("|",
                Objects.toString(promotion.getName(), ""),
                Objects.toString(promotion.getDiscountPct(), ""),
                Objects.toString(promotion.getStartDate(), ""),
                Objects.toString(promotion.getEndDate(), ""),
                Boolean.toString(promotion.isActive()));
    }

    public void recalculateTotalStock(UUID productId) {
        jpa.recalculateTotalStock(productId);
    }

    void recalculateAllTotalStock() {
        jpa.recalculateAllTotalStock();
    }
}
