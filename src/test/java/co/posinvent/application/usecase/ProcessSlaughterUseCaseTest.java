package co.posinvent.application.usecase;

import co.posinvent.application.dto.SlaughterRequest;
import co.posinvent.domain.exception.BusinessException;
import co.posinvent.domain.exception.ResourceNotFoundException;
import co.posinvent.domain.model.Animal;
import co.posinvent.domain.model.Animal.AnimalStatus;
import co.posinvent.domain.model.Animal.Species;
import co.posinvent.domain.model.Batch;
import co.posinvent.domain.model.Batch.BatchStatus;
import co.posinvent.domain.model.InventoryStock;
import co.posinvent.domain.model.MovementType;
import co.posinvent.domain.model.Product;
import co.posinvent.domain.model.Slaughter;
import co.posinvent.domain.model.Slaughter.SlaughterSourceType;
import co.posinvent.domain.model.ThirdParty;
import co.posinvent.domain.model.Warehouse;
import co.posinvent.domain.model.Warehouse.WarehouseType;
import co.posinvent.domain.repository.AnimalRepository;
import co.posinvent.domain.repository.BatchRepository;
import co.posinvent.domain.repository.ProductRepository;
import co.posinvent.domain.repository.SlaughterRepository;
import co.posinvent.domain.repository.StockRepository;
import co.posinvent.domain.repository.ThirdPartyRepository;
import co.posinvent.domain.repository.WarehouseRepository;
import co.posinvent.domain.service.SlaughterDomainService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProcessSlaughterUseCaseTest {

    @Mock
    private AnimalRepository animalRepository;
    @Mock
    private BatchRepository batchRepository;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private ThirdPartyRepository thirdPartyRepository;
    @Mock
    private WarehouseRepository warehouseRepository;
    @Mock
    private SlaughterRepository slaughterRepository;
    @Mock
    private StockRepository stockRepository;
    @Mock
    private RecordMovementUseCase recordMovement;
    @Mock
    private OptimisticConcurrencyExecutor concurrencyExecutor;

    private ProcessSlaughterUseCase useCase;

    private static final UUID ANIMAL_ID = UUID.randomUUID();
    private static final UUID SUPPLIER_ID = UUID.randomUUID();
    private static final UUID INSPECTOR_ID = UUID.randomUUID();
    private static final UUID CANAL_PRODUCT_ID = UUID.randomUUID();
    private static final UUID CANAL_WAREHOUSE_ID = UUID.randomUUID();
    private static final UUID OPERATOR_ID = UUID.randomUUID();
    private static final UUID BATCH_ID = UUID.randomUUID();
    private static final UUID SLAUGHTER_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        useCase = new ProcessSlaughterUseCase(
                animalRepository, batchRepository, productRepository, thirdPartyRepository,
                warehouseRepository, slaughterRepository, stockRepository,
                new SlaughterDomainService(), recordMovement, concurrencyExecutor);
        doAnswer(inv -> ((Supplier<?>) inv.getArgument(0)).get())
                .when(concurrencyExecutor).execute(any());
    }

    @Test
    void process_createsBatchStockMovementAndMarksAnimalSlaughtered() {
        when(animalRepository.findById(ANIMAL_ID)).thenReturn(Optional.of(animal(AnimalStatus.RECEIVED, "100")));
        when(thirdPartyRepository.findById(INSPECTOR_ID)).thenReturn(Optional.of(inspector()));
        when(productRepository.findByProductCode("CANAL")).thenReturn(Optional.of(canalProduct()));
        when(warehouseRepository.findFirstActiveByType(WarehouseType.CANAL)).thenReturn(Optional.of(canalWarehouse()));
        when(batchRepository.save(any(Batch.class))).thenAnswer(inv -> withBatchId(inv.getArgument(0)));
        when(stockRepository.findByProductBatchWarehouse(CANAL_PRODUCT_ID, BATCH_ID, CANAL_WAREHOUSE_ID))
                .thenReturn(Optional.empty());
        doAnswer(inv -> inv.getArgument(0)).when(stockRepository).save(any(InventoryStock.class));
        when(slaughterRepository.save(any(Slaughter.class))).thenAnswer(inv -> withSlaughterId(inv.getArgument(0)));

        var response = useCase.process(request(), OPERATOR_ID);

        assertThat(response.animalId()).isEqualTo(ANIMAL_ID);
        assertThat(response.carcassWeight()).isEqualByComparingTo("80");
        assertThat(response.yieldPercentage()).isEqualByComparingTo("80.00");
        assertThat(response.batchId()).isEqualTo(BATCH_ID);

        var batchCaptor = ArgumentCaptor.forClass(Batch.class);
        verify(batchRepository).save(batchCaptor.capture());
        var savedBatch = batchCaptor.getValue();
        assertThat(savedBatch.productId()).isEqualTo(CANAL_PRODUCT_ID);
        assertThat(savedBatch.supplierId()).isEqualTo(SUPPLIER_ID);
        assertThat(savedBatch.warehouseId()).isEqualTo(CANAL_WAREHOUSE_ID);
        assertThat(savedBatch.status()).isEqualTo(BatchStatus.OPEN);
        assertThat(savedBatch.createdBy()).isEqualTo(OPERATOR_ID);

        var stockCaptor = ArgumentCaptor.forClass(InventoryStock.class);
        verify(stockRepository).save(stockCaptor.capture());
        assertThat(stockCaptor.getValue().currentQuantity()).isEqualByComparingTo("80");
        // unit cost = purchaseCost / carcassWeight = 800 / 80 = 10.000000
        assertThat(stockCaptor.getValue().unitCost()).isEqualByComparingTo("10.000000");

        verify(recordMovement).record(
                eq(CANAL_PRODUCT_ID), eq(BATCH_ID), eq(CANAL_WAREHOUSE_ID), eq(MovementType.ENTRY),
                eq(new BigDecimal("80")), eq(new BigDecimal("10.000000")),
                eq(BigDecimal.ZERO), eq(new BigDecimal("80")),
                eq("SLAUGHTER"), eq(BATCH_ID), any());

        var animalCaptor = ArgumentCaptor.forClass(Animal.class);
        verify(animalRepository).save(animalCaptor.capture());
        assertThat(animalCaptor.getValue().status()).isEqualTo(AnimalStatus.SLAUGHTERED);
        assertThat(animalCaptor.getValue().id()).isEqualTo(ANIMAL_ID);

        verify(slaughterRepository).save(any(Slaughter.class));
        verify(productRepository).recalculateTotalStock(CANAL_PRODUCT_ID);
    }

    @Test
    void process_throwsWhenAnimalNotFound() {
        when(animalRepository.findById(ANIMAL_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.process(request(), OPERATOR_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Animal");

        verify(slaughterRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void process_throwsWhenAnimalAlreadySlaughtered() {
        when(animalRepository.findById(ANIMAL_ID)).thenReturn(Optional.of(animal(AnimalStatus.SLAUGHTERED, "100")));

        assertThatThrownBy(() -> useCase.process(request(), OPERATOR_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", "ANIMAL_ALREADY_SLAUGHTERED");

        verify(slaughterRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void process_throwsWhenInspectorNotFound() {
        when(animalRepository.findById(ANIMAL_ID)).thenReturn(Optional.of(animal(AnimalStatus.RECEIVED, "100")));
        when(thirdPartyRepository.findById(INSPECTOR_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.process(request(), OPERATOR_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Inspector");

        verify(slaughterRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void process_throwsWhenCanalProductMissing() {
        when(animalRepository.findById(ANIMAL_ID)).thenReturn(Optional.of(animal(AnimalStatus.RECEIVED, "100")));
        when(thirdPartyRepository.findById(INSPECTOR_ID)).thenReturn(Optional.of(inspector()));
        when(productRepository.findByProductCode("CANAL")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.process(request(), OPERATOR_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", "CANAL_PRODUCT_NOT_FOUND");

        verify(slaughterRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void process_throwsWhenCanalWarehouseMissing() {
        when(animalRepository.findById(ANIMAL_ID)).thenReturn(Optional.of(animal(AnimalStatus.RECEIVED, "100")));
        when(thirdPartyRepository.findById(INSPECTOR_ID)).thenReturn(Optional.of(inspector()));
        when(productRepository.findByProductCode("CANAL")).thenReturn(Optional.of(canalProduct()));
        when(warehouseRepository.findFirstActiveByType(WarehouseType.CANAL)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.process(request(), OPERATOR_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", "CANAL_WAREHOUSE_NOT_FOUND");

        verify(slaughterRepository, org.mockito.Mockito.never()).save(any());
    }

    // ── Helpers ─────────────────────────────────────────────────────────

    private SlaughterRequest request() {
        return new SlaughterRequest(
                ANIMAL_ID,
                SlaughterSourceType.MANUAL,
                "Justificación de faena manual",
                new BigDecimal("80"),
                new BigDecimal("800"),
                "PLANTA-INVIMA-01",
                INSPECTOR_ID,
                LocalDate.now(),
                "Faena de prueba"
        );
    }

    private Animal animal(AnimalStatus status, String liveWeight) {
        return new Animal(
                ANIMAL_ID,
                "ICA-001",
                SUPPLIER_ID,
                Species.PORCINO,
                new BigDecimal(liveWeight),
                LocalDate.now().minusDays(1),
                status,
                null,
                OPERATOR_ID,
                OffsetDateTime.now().minusDays(1),
                null
        );
    }

    private ThirdParty inspector() {
        return new ThirdParty(
                INSPECTOR_ID, "000000000-0", "INSPECTOR",
                ThirdParty.ThirdPartyType.EMPLOYEE, null,
                BigDecimal.ZERO, BigDecimal.ZERO,
                ThirdParty.PersonType.JURIDICA, ThirdParty.TaxRegime.ORDINARIO,
                List.of(), null, null, true,
                OffsetDateTime.now(), OffsetDateTime.now(),
                null, null, null, null, null, null, null, null,
                null, null, null, null, 0,
                null, null, null, null, null, null, null, null,
                false, false, false, false, false, null
        );
    }

    private Product canalProduct() {
        return new Product(
                CANAL_PRODUCT_ID, "CANAL", "Producto CANAL",
                null, null, null, null, null, null, null, null, null, null,
                BigDecimal.ONE, BigDecimal.ZERO, "EXENTO", BigDecimal.TEN,
                "PROMEDIO_PONDERADO",
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.TEN, BigDecimal.ZERO,
                false, false, false, false, false, false, true,
                null, null, null, null, null, null, null,
                true, 0,
                OffsetDateTime.now().minusDays(2), OffsetDateTime.now().minusDays(1),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
    }

    private Warehouse canalWarehouse() {
        return new Warehouse(
                CANAL_WAREHOUSE_ID, "Bodega CANAL", "Ubicación",
                WarehouseType.CANAL, true, OffsetDateTime.now().minusDays(3));
    }

    private Batch withBatchId(Batch b) {
        return new Batch(
                BATCH_ID, b.productId(), b.supplierId(), b.warehouseId(), b.entryDate(),
                b.initialWeight(), b.purchaseCost(), b.status(), b.notes(), b.expirationDate(),
                b.createdBy(), b.createdAt(), b.updatedAt(), b.updatedBy(), b.sourceReceiptId(),
                b.ocId(), b.productName(), b.supplierName(), b.warehouseName(), b.parentBatchId(),
                b.batchType(), b.unitOfMeasureId());
    }

    private Slaughter withSlaughterId(Slaughter s) {
        return new Slaughter(
                SLAUGHTER_ID, s.animalId(), s.carcassWeight(), s.yieldPercentage(),
                s.slaughterDate(), s.invimaPlant(), s.inspectorId(), s.sourceType(),
                s.justification(), s.purchaseCost(), s.batchId(), s.notes(),
                s.createdBy(), s.createdAt(), s.updatedAt());
    }
}
