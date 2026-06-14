package co.posinvent.infrastructure.adapters.out.persistence;

import co.posinvent.domain.model.Product;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductRepositoryAdapterTest {

    @Mock
    private ProductJpaRepository productJpa;
    @Mock
    private ProductMapper mapper;
    @Mock
    private ProductTypeJpaRepository productTypeJpa;
    @Mock
    private ProductStateJpaRepository productStateJpa;
    @Mock
    private BrandJpaRepository brandJpa;
    @Mock
    private ProductModelJpaRepository modelJpa;
    @Mock
    private ProductCategoryJpaRepository categoryJpa;
    @Mock
    private ProductGroupJpaRepository groupJpa;
    @Mock
    private UnitOfMeasureJpaRepository unitOfMeasureJpa;
    @Mock
    private PucAccountJpaRepository pucAccountJpa;
    @Mock
    private ProductWarehouseJpaRepository warehouseJpa;
    @Mock
    private ProductSupplierJpaRepository supplierJpa;
    @Mock
    private ProductImageJpaRepository imageJpa;
    @Mock
    private ProductPromotionJpaRepository promotionJpa;
    @Mock
    private ProductPriceEntryJpaRepository priceEntryJpa;
    @Mock
    private ProductPresentationJpaRepository presentationJpa;

    private ProductRepositoryAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new ProductRepositoryAdapter(
                productJpa,
                mapper,
                productTypeJpa,
                productStateJpa,
                brandJpa,
                modelJpa,
                categoryJpa,
                groupJpa,
                unitOfMeasureJpa,
                pucAccountJpa,
                warehouseJpa,
                supplierJpa,
                imageJpa,
                promotionJpa,
                priceEntryJpa,
                presentationJpa
        );
    }

    @Test
    void save_updatesExistingWarehouseAndDeletesOnlyMissingRows() {
        var productId = UUID.randomUUID();
        var keepWarehouseId = UUID.randomUUID();
        var removeWarehouseId = UUID.randomUUID();
        var keepIdentity = UUID.randomUUID();
        var removeIdentity = UUID.randomUUID();
        var newIdentity = UUID.randomUUID();

        var savedEntity = savedProductEntity(productId);
        var existingToKeep = warehouseEntity(keepWarehouseId, savedEntity, keepIdentity, UUID.randomUUID(), UUID.randomUUID(), false);
        var existingToRemove = warehouseEntity(removeWarehouseId, savedEntity, removeIdentity, UUID.randomUUID(), UUID.randomUUID(), true);

        stubBasePersistence(productId, savedEntity);
        when(warehouseJpa.findByProductId(productId)).thenReturn(
                List.of(existingToKeep, existingToRemove),
                List.of(existingToKeep)
        );

        var product = productWithWarehouses(productId, List.of(
                new Product.ProductWarehouse(keepWarehouseId, keepIdentity, UUID.randomUUID(), UUID.randomUUID(), true),
                new Product.ProductWarehouse(null, newIdentity, UUID.randomUUID(), UUID.randomUUID(), false)
        ));

        adapter.save(product);

        var deleted = captureWarehousesDeleted();
        assertThat(deleted).containsExactly(existingToRemove);
        verify(warehouseJpa).flush();
        verify(warehouseJpa, never()).deleteByProductId(productId);

        var savedWarehouses = captureWarehousesSaved();
        assertThat(savedWarehouses).hasSize(2);
        assertThat(savedWarehouses).contains(existingToKeep);

        assertThat(field(existingToKeep, "id", UUID.class)).isEqualTo(keepWarehouseId);
        assertThat(field(existingToKeep, "warehouseId", UUID.class)).isEqualTo(keepIdentity);
        assertThat(field(existingToKeep, "isDefault", Boolean.class)).isTrue();

        var created = savedWarehouses.stream()
                .filter(entity -> entity != existingToKeep)
                .findFirst()
                .orElseThrow();

        assertThat(field(created, "id", UUID.class)).isNull();
        assertThat(field(created, "product", ProductEntity.class)).isSameAs(savedEntity);
        assertThat(field(created, "warehouseId", UUID.class)).isEqualTo(newIdentity);
    }

    @Test
    void save_reusesWarehouseByIdentityWhenRequestDoesNotSendChildId() {
        var productId = UUID.randomUUID();
        var existingWarehouseId = UUID.randomUUID();
        var warehouseIdentity = UUID.randomUUID();
        var savedEntity = savedProductEntity(productId);
        var existing = warehouseEntity(existingWarehouseId, savedEntity, warehouseIdentity, UUID.randomUUID(), UUID.randomUUID(), false);
        var newLocationId = UUID.randomUUID();
        var newUnitOfMeasureId = UUID.randomUUID();

        stubBasePersistence(productId, savedEntity);
        when(warehouseJpa.findByProductId(productId)).thenReturn(List.of(existing), List.of(existing));

        var product = productWithWarehouses(productId, List.of(
                new Product.ProductWarehouse(null, warehouseIdentity, newLocationId, newUnitOfMeasureId, true)
        ));

        adapter.save(product);

        verify(warehouseJpa, never()).deleteAll(any());
        verify(warehouseJpa, never()).flush();
        verify(warehouseJpa, never()).deleteByProductId(productId);

        var savedWarehouses = captureWarehousesSaved();
        assertThat(savedWarehouses).containsExactly(existing);
        assertThat(field(existing, "id", UUID.class)).isEqualTo(existingWarehouseId);
        assertThat(field(existing, "warehouseId", UUID.class)).isEqualTo(warehouseIdentity);
        assertThat(field(existing, "locationId", UUID.class)).isEqualTo(newLocationId);
        assertThat(field(existing, "unitOfMeasureId", UUID.class)).isEqualTo(newUnitOfMeasureId);
        assertThat(field(existing, "isDefault", Boolean.class)).isTrue();
    }

    @Test
    void save_updatesExistingSupplierAndDeletesOnlyMissingRows() {
        var productId = UUID.randomUUID();
        var keepSupplierId = UUID.randomUUID();
        var removeSupplierId = UUID.randomUUID();
        var keepIdentity = UUID.randomUUID();
        var removeIdentity = UUID.randomUUID();
        var newIdentity = UUID.randomUUID();

        var savedEntity = savedProductEntity(productId);
        var existingToKeep = supplierEntity(keepSupplierId, savedEntity, keepIdentity, "OLD-REF", new BigDecimal("11.10"), false);
        var existingToRemove = supplierEntity(removeSupplierId, savedEntity, removeIdentity, "REMOVE", new BigDecimal("12.20"), true);

        stubBasePersistence(productId, savedEntity);
        when(supplierJpa.findByProductId(productId)).thenReturn(
                List.of(existingToKeep, existingToRemove),
                List.of(existingToKeep)
        );

        var product = productWithSuppliers(productId, List.of(
                new Product.ProductSupplier(keepSupplierId, keepIdentity, "NEW-REF", new BigDecimal("13.30"), true),
                new Product.ProductSupplier(null, newIdentity, "CREATED", new BigDecimal("14.40"), false)
        ));

        adapter.save(product);

        var deleted = captureSuppliersDeleted();
        assertThat(deleted).containsExactly(existingToRemove);
        verify(supplierJpa).flush();
        verify(supplierJpa, never()).deleteByProductId(productId);

        var savedSuppliers = captureSuppliersSaved();
        assertThat(savedSuppliers).hasSize(2);
        assertThat(savedSuppliers).contains(existingToKeep);

        assertThat(field(existingToKeep, "id", UUID.class)).isEqualTo(keepSupplierId);
        assertThat(field(existingToKeep, "supplierId", UUID.class)).isEqualTo(keepIdentity);
        assertThat(field(existingToKeep, "supplierReference", String.class)).isEqualTo("NEW-REF");
        assertThat(field(existingToKeep, "unitCost", BigDecimal.class)).isEqualByComparingTo("13.30");
        assertThat(field(existingToKeep, "isMain", Boolean.class)).isTrue();

        var created = savedSuppliers.stream()
                .filter(entity -> entity != existingToKeep)
                .findFirst()
                .orElseThrow();

        assertThat(field(created, "id", UUID.class)).isNull();
        assertThat(field(created, "product", ProductEntity.class)).isSameAs(savedEntity);
        assertThat(field(created, "supplierId", UUID.class)).isEqualTo(newIdentity);
    }

    @Test
    void save_reusesSupplierByIdentityWhenRequestDoesNotSendChildId() {
        var productId = UUID.randomUUID();
        var existingSupplierId = UUID.randomUUID();
        var supplierIdentity = UUID.randomUUID();
        var savedEntity = savedProductEntity(productId);
        var existing = supplierEntity(existingSupplierId, savedEntity, supplierIdentity, "OLD-REF", new BigDecimal("9.90"), false);

        stubBasePersistence(productId, savedEntity);
        when(supplierJpa.findByProductId(productId)).thenReturn(List.of(existing), List.of(existing));

        var product = productWithSuppliers(productId, List.of(
                new Product.ProductSupplier(null, supplierIdentity, "UPDATED-REF", new BigDecimal("15.50"), true)
        ));

        adapter.save(product);

        verify(supplierJpa, never()).deleteAll(any());
        verify(supplierJpa, never()).flush();
        verify(supplierJpa, never()).deleteByProductId(productId);

        var savedSuppliers = captureSuppliersSaved();
        assertThat(savedSuppliers).containsExactly(existing);
        assertThat(field(existing, "id", UUID.class)).isEqualTo(existingSupplierId);
        assertThat(field(existing, "supplierId", UUID.class)).isEqualTo(supplierIdentity);
        assertThat(field(existing, "supplierReference", String.class)).isEqualTo("UPDATED-REF");
        assertThat(field(existing, "unitCost", BigDecimal.class)).isEqualByComparingTo("15.50");
        assertThat(field(existing, "isMain", Boolean.class)).isTrue();
    }

    @Test
    void save_updatesExistingPromotionAndDeletesOnlyMissingRows() {
        var productId = UUID.randomUUID();
        var keepPromotionId = UUID.randomUUID();
        var removePromotionId = UUID.randomUUID();
        var savedEntity = savedProductEntity(productId);
        var existingToKeep = promotionEntity(
                keepPromotionId,
                savedEntity,
                "Promo Keep",
                new BigDecimal("10.00"),
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 31),
                false
        );
        var existingToRemove = promotionEntity(
                removePromotionId,
                savedEntity,
                "Promo Remove",
                new BigDecimal("15.00"),
                LocalDate.of(2026, 2, 1),
                LocalDate.of(2026, 2, 28),
                true
        );

        stubBasePersistence(productId, savedEntity);
        when(promotionJpa.findByProductId(productId)).thenReturn(
                List.of(existingToKeep, existingToRemove),
                List.of(existingToKeep)
        );

        var product = productWithPromotions(productId, List.of(
                new Product.ProductPromotion(
                        keepPromotionId,
                        "Promo Keep Updated",
                        new BigDecimal("12.50"),
                        LocalDate.of(2026, 1, 5),
                        LocalDate.of(2026, 2, 5),
                        true
                ),
                new Product.ProductPromotion(
                        null,
                        "Promo New",
                        new BigDecimal("20.00"),
                        LocalDate.of(2026, 3, 1),
                        LocalDate.of(2026, 3, 31),
                        true
                )
        ));

        adapter.save(product);

        var deleted = capturePromotionsDeleted();
        assertThat(deleted).containsExactly(existingToRemove);
        verify(promotionJpa).flush();
        verify(promotionJpa, never()).deleteByProductId(productId);

        var savedPromotions = capturePromotionsSaved();
        assertThat(savedPromotions).hasSize(2);
        assertThat(savedPromotions).contains(existingToKeep);

        assertThat(field(existingToKeep, "id", UUID.class)).isEqualTo(keepPromotionId);
        assertThat(field(existingToKeep, "name", String.class)).isEqualTo("Promo Keep Updated");
        assertThat(field(existingToKeep, "discountPct", BigDecimal.class)).isEqualByComparingTo("12.50");
        assertThat(field(existingToKeep, "startDate", LocalDate.class)).isEqualTo(LocalDate.of(2026, 1, 5));
        assertThat(field(existingToKeep, "endDate", LocalDate.class)).isEqualTo(LocalDate.of(2026, 2, 5));
        assertThat(field(existingToKeep, "isActive", Boolean.class)).isTrue();

        var created = savedPromotions.stream()
                .filter(entity -> entity != existingToKeep)
                .findFirst()
                .orElseThrow();

        assertThat(field(created, "id", UUID.class)).isNull();
        assertThat(field(created, "product", ProductEntity.class)).isSameAs(savedEntity);
        assertThat(field(created, "name", String.class)).isEqualTo("Promo New");
    }

    @Test
    void save_reusesPromotionByIdentityWhenRequestDoesNotSendChildId() {
        var productId = UUID.randomUUID();
        var existingPromotionId = UUID.randomUUID();
        var savedEntity = savedProductEntity(productId);
        var existing = promotionEntity(
                existingPromotionId,
                savedEntity,
                "Promo Identity",
                new BigDecimal("18.00"),
                LocalDate.of(2026, 4, 1),
                LocalDate.of(2026, 4, 30),
                true
        );

        stubBasePersistence(productId, savedEntity);
        when(promotionJpa.findByProductId(productId)).thenReturn(List.of(existing), List.of(existing));

        var product = productWithPromotions(productId, List.of(
                new Product.ProductPromotion(
                        null,
                        "Promo Identity",
                        new BigDecimal("18.00"),
                        LocalDate.of(2026, 4, 1),
                        LocalDate.of(2026, 4, 30),
                        true
                )
        ));

        adapter.save(product);

        verify(promotionJpa, never()).deleteAll(any());
        verify(promotionJpa, never()).flush();
        verify(promotionJpa, never()).deleteByProductId(productId);

        var savedPromotions = capturePromotionsSaved();
        assertThat(savedPromotions).containsExactly(existing);
        assertThat(field(existing, "id", UUID.class)).isEqualTo(existingPromotionId);
        assertThat(field(existing, "name", String.class)).isEqualTo("Promo Identity");
        assertThat(field(existing, "discountPct", BigDecimal.class)).isEqualByComparingTo("18.00");
        assertThat(field(existing, "startDate", LocalDate.class)).isEqualTo(LocalDate.of(2026, 4, 1));
        assertThat(field(existing, "endDate", LocalDate.class)).isEqualTo(LocalDate.of(2026, 4, 30));
        assertThat(field(existing, "isActive", Boolean.class)).isTrue();
    }

    @Test
    void save_updatesExistingPriceEntryAndDeletesOnlyMissingRows() {
        var productId = UUID.randomUUID();
        var keepPriceEntryId = UUID.randomUUID();
        var removePriceEntryId = UUID.randomUUID();
        var keepIdentity = UUID.randomUUID();
        var removeIdentity = UUID.randomUUID();
        var newIdentity = UUID.randomUUID();

        var savedEntity = savedProductEntity(productId);
        var existingToKeep = priceEntryEntity(keepPriceEntryId, savedEntity, keepIdentity, new BigDecimal("10.00"), new BigDecimal("20.00"));
        var existingToRemove = priceEntryEntity(removePriceEntryId, savedEntity, removeIdentity, new BigDecimal("11.00"), new BigDecimal("21.00"));

        stubBasePersistence(productId, savedEntity);
        when(priceEntryJpa.findByProductId(productId)).thenReturn(
                List.of(existingToKeep, existingToRemove),
                List.of(existingToKeep)
        );

        var product = productWithPriceEntries(productId, List.of(
                new Product.ProductPriceEntry(keepPriceEntryId, keepIdentity, new BigDecimal("1.2200"), new BigDecimal("22.00")),
                new Product.ProductPriceEntry(null, newIdentity, new BigDecimal("1.2300"), new BigDecimal("23.00"))
        ));

        adapter.save(product);

        var deleted = capturePriceEntriesDeleted();
        assertThat(deleted).containsExactly(existingToRemove);
        verify(priceEntryJpa).flush();
        verify(priceEntryJpa, never()).deleteByProductId(productId);

        var savedPriceEntries = capturePriceEntriesSaved();
        assertThat(savedPriceEntries).hasSize(2);
        assertThat(savedPriceEntries).contains(existingToKeep);

        assertThat(field(existingToKeep, "id", UUID.class)).isEqualTo(keepPriceEntryId);
        assertThat(field(existingToKeep, "priceListId", UUID.class)).isEqualTo(keepIdentity);
        assertThat(field(existingToKeep, "price", BigDecimal.class)).isEqualByComparingTo("1.2200");
        assertThat(field(existingToKeep, "profitMargin", BigDecimal.class)).isEqualByComparingTo("22.00");

        var created = savedPriceEntries.stream()
                .filter(entity -> entity != existingToKeep)
                .findFirst()
                .orElseThrow();

        assertThat(field(created, "id", UUID.class)).isNull();
        assertThat(field(created, "product", ProductEntity.class)).isSameAs(savedEntity);
        assertThat(field(created, "priceListId", UUID.class)).isEqualTo(newIdentity);
    }

    @Test
    void save_reusesPriceEntryByIdentityWhenRequestDoesNotSendChildId() {
        var productId = UUID.randomUUID();
        var existingPriceEntryId = UUID.randomUUID();
        var priceListIdentity = UUID.randomUUID();
        var savedEntity = savedProductEntity(productId);
        var existing = priceEntryEntity(existingPriceEntryId, savedEntity, priceListIdentity, new BigDecimal("9.00"), new BigDecimal("19.00"));

        stubBasePersistence(productId, savedEntity);
        when(priceEntryJpa.findByProductId(productId)).thenReturn(List.of(existing), List.of(existing));

        var product = productWithPriceEntries(productId, List.of(
                new Product.ProductPriceEntry(null, priceListIdentity, new BigDecimal("1.2700"), new BigDecimal("27.00"))
        ));

        adapter.save(product);

        verify(priceEntryJpa, never()).deleteAll(any());
        verify(priceEntryJpa, never()).flush();
        verify(priceEntryJpa, never()).deleteByProductId(productId);

        var savedPriceEntries = capturePriceEntriesSaved();
        assertThat(savedPriceEntries).containsExactly(existing);
        assertThat(field(existing, "id", UUID.class)).isEqualTo(existingPriceEntryId);
        assertThat(field(existing, "priceListId", UUID.class)).isEqualTo(priceListIdentity);
        assertThat(field(existing, "price", BigDecimal.class)).isEqualByComparingTo("1.2700");
        assertThat(field(existing, "profitMargin", BigDecimal.class)).isEqualByComparingTo("27.00");
    }

    @Test
    void save_rejectsInvalidAggregateBeforePersisting() {
        var product = productWithWarehouses(UUID.randomUUID(), List.of(
                new Product.ProductWarehouse(null, UUID.randomUUID(), null, null, true),
                new Product.ProductWarehouse(null, UUID.randomUUID(), null, null, true)
        ));

        assertThatThrownBy(() -> adapter.save(product))
                .hasMessage("El producto solo puede tener una bodega predeterminada.")
                .isInstanceOf(co.posinvent.domain.exception.BusinessException.class);

        verifyNoInteractions(productJpa, mapper, warehouseJpa, supplierJpa, imageJpa, promotionJpa, priceEntryJpa);
    }

    private void stubBasePersistence(UUID productId, ProductEntity savedEntity) {
        when(mapper.toEntityBase(any(Product.class))).thenReturn(savedEntity);
        when(productJpa.save(savedEntity)).thenReturn(savedEntity);
        when(productJpa.findById(productId)).thenReturn(Optional.of(savedEntity));
        when(mapper.toDomain(savedEntity)).thenReturn(productWithWarehouses(productId, List.of()));
        when(supplierJpa.findByProductId(productId)).thenReturn(List.of());
        when(imageJpa.findByProductId(productId)).thenReturn(List.of());
        when(promotionJpa.findByProductId(productId)).thenReturn(List.of());
        when(priceEntryJpa.findByProductId(productId)).thenReturn(List.of());
    }

    @SuppressWarnings("unchecked")
    private List<ProductWarehouseEntity> captureWarehousesSaved() {
        var captor = ArgumentCaptor.forClass(Iterable.class);
        verify(warehouseJpa).saveAll(captor.capture());
        var saved = new ArrayList<ProductWarehouseEntity>();
        captor.getValue().forEach(entity -> saved.add((ProductWarehouseEntity) entity));
        return saved;
    }

    @SuppressWarnings("unchecked")
    private List<ProductWarehouseEntity> captureWarehousesDeleted() {
        var captor = ArgumentCaptor.forClass(Iterable.class);
        verify(warehouseJpa).deleteAll(captor.capture());
        var deleted = new ArrayList<ProductWarehouseEntity>();
        captor.getValue().forEach(entity -> deleted.add((ProductWarehouseEntity) entity));
        return deleted;
    }

    @SuppressWarnings("unchecked")
    private List<ProductSupplierEntity> captureSuppliersSaved() {
        var captor = ArgumentCaptor.forClass(Iterable.class);
        verify(supplierJpa).saveAll(captor.capture());
        var saved = new ArrayList<ProductSupplierEntity>();
        captor.getValue().forEach(entity -> saved.add((ProductSupplierEntity) entity));
        return saved;
    }

    @SuppressWarnings("unchecked")
    private List<ProductSupplierEntity> captureSuppliersDeleted() {
        var captor = ArgumentCaptor.forClass(Iterable.class);
        verify(supplierJpa).deleteAll(captor.capture());
        var deleted = new ArrayList<ProductSupplierEntity>();
        captor.getValue().forEach(entity -> deleted.add((ProductSupplierEntity) entity));
        return deleted;
    }

    @SuppressWarnings("unchecked")
    private List<ProductPromotionEntity> capturePromotionsSaved() {
        var captor = ArgumentCaptor.forClass(Iterable.class);
        verify(promotionJpa).saveAll(captor.capture());
        var saved = new ArrayList<ProductPromotionEntity>();
        captor.getValue().forEach(entity -> saved.add((ProductPromotionEntity) entity));
        return saved;
    }

    @SuppressWarnings("unchecked")
    private List<ProductPromotionEntity> capturePromotionsDeleted() {
        var captor = ArgumentCaptor.forClass(Iterable.class);
        verify(promotionJpa).deleteAll(captor.capture());
        var deleted = new ArrayList<ProductPromotionEntity>();
        captor.getValue().forEach(entity -> deleted.add((ProductPromotionEntity) entity));
        return deleted;
    }

    @SuppressWarnings("unchecked")
    private List<ProductPriceEntryEntity> capturePriceEntriesSaved() {
        var captor = ArgumentCaptor.forClass(Iterable.class);
        verify(priceEntryJpa).saveAll(captor.capture());
        var saved = new ArrayList<ProductPriceEntryEntity>();
        captor.getValue().forEach(entity -> saved.add((ProductPriceEntryEntity) entity));
        return saved;
    }

    @SuppressWarnings("unchecked")
    private List<ProductPriceEntryEntity> capturePriceEntriesDeleted() {
        var captor = ArgumentCaptor.forClass(Iterable.class);
        verify(priceEntryJpa).deleteAll(captor.capture());
        var deleted = new ArrayList<ProductPriceEntryEntity>();
        captor.getValue().forEach(entity -> deleted.add((ProductPriceEntryEntity) entity));
        return deleted;
    }

    private ProductEntity savedProductEntity(UUID productId) {
        var entity = new ProductEntity();
        ReflectionTestUtils.setField(entity, "id", productId);
        return entity;
    }

    private ProductWarehouseEntity warehouseEntity(
            UUID id,
            ProductEntity product,
            UUID warehouseId,
            UUID locationId,
            UUID unitOfMeasureId,
            boolean isDefault) {

        var entity = new ProductWarehouseEntity();
        ReflectionTestUtils.setField(entity, "id", id);
        ReflectionTestUtils.setField(entity, "product", product);
        ReflectionTestUtils.setField(entity, "warehouseId", warehouseId);
        ReflectionTestUtils.setField(entity, "locationId", locationId);
        ReflectionTestUtils.setField(entity, "unitOfMeasureId", unitOfMeasureId);
        ReflectionTestUtils.setField(entity, "isDefault", isDefault);
        return entity;
    }

    private ProductSupplierEntity supplierEntity(
            UUID id,
            ProductEntity product,
            UUID supplierId,
            String supplierReference,
            BigDecimal unitCost,
            boolean isMain) {

        var entity = new ProductSupplierEntity();
        ReflectionTestUtils.setField(entity, "id", id);
        ReflectionTestUtils.setField(entity, "product", product);
        ReflectionTestUtils.setField(entity, "supplierId", supplierId);
        ReflectionTestUtils.setField(entity, "supplierReference", supplierReference);
        ReflectionTestUtils.setField(entity, "unitCost", unitCost);
        ReflectionTestUtils.setField(entity, "isMain", isMain);
        return entity;
    }

    private ProductPromotionEntity promotionEntity(
            UUID id,
            ProductEntity product,
            String name,
            BigDecimal discountPct,
            LocalDate startDate,
            LocalDate endDate,
            boolean isActive) {

        var entity = new ProductPromotionEntity();
        ReflectionTestUtils.setField(entity, "id", id);
        ReflectionTestUtils.setField(entity, "product", product);
        ReflectionTestUtils.setField(entity, "name", name);
        ReflectionTestUtils.setField(entity, "discountPct", discountPct);
        ReflectionTestUtils.setField(entity, "startDate", startDate);
        ReflectionTestUtils.setField(entity, "endDate", endDate);
        ReflectionTestUtils.setField(entity, "isActive", isActive);
        return entity;
    }

    private ProductPriceEntryEntity priceEntryEntity(
            UUID id,
            ProductEntity product,
            UUID priceListId,
            BigDecimal price,
            BigDecimal profitMargin) {

        var entity = new ProductPriceEntryEntity();
        ReflectionTestUtils.setField(entity, "id", id);
        ReflectionTestUtils.setField(entity, "product", product);
        ReflectionTestUtils.setField(entity, "priceListId", priceListId);
        ReflectionTestUtils.setField(entity, "price", price);
        ReflectionTestUtils.setField(entity, "profitMargin", profitMargin);
        return entity;
    }

    private <T> T field(Object target, String name, Class<T> type) {
        return type.cast(ReflectionTestUtils.getField(target, name));
    }

    private Product productWithWarehouses(UUID productId, List<Product.ProductWarehouse> warehouses) {
        return product(productId, warehouses, List.of(), List.of(), List.of());
    }

    private Product productWithSuppliers(UUID productId, List<Product.ProductSupplier> suppliers) {
        return product(productId, List.of(), suppliers, List.of(), List.of());
    }

    private Product productWithPromotions(UUID productId, List<Product.ProductPromotion> promotions) {
        return product(productId, List.of(), List.of(), promotions, List.of());
    }

    private Product productWithPriceEntries(UUID productId, List<Product.ProductPriceEntry> priceEntries) {
        return product(productId, List.of(), List.of(), List.of(), priceEntries);
    }

    private Product product(
            UUID productId,
            List<Product.ProductWarehouse> warehouses,
            List<Product.ProductSupplier> suppliers,
            List<Product.ProductPromotion> promotions,
            List<Product.ProductPriceEntry> priceEntries) {

        var now = OffsetDateTime.now();
        return new Product(
                productId,
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
                BigDecimal.ONE,
                "IVA",
                new BigDecimal("1.0100"),
                "FIFO",
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
                warehouses,
                suppliers,
                List.of(),
                promotions,
                priceEntries,
                List.of()
        );
    }
}
