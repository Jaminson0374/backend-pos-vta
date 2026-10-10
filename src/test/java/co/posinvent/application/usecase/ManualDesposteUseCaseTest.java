package co.posinvent.application.usecase;

import co.posinvent.application.dto.ManualDesposteRequest;
import co.posinvent.domain.model.Batch;
import co.posinvent.domain.model.BatchType;
import co.posinvent.domain.model.Desposte;
import co.posinvent.domain.model.InventoryStock;
import co.posinvent.domain.model.Product;
import co.posinvent.domain.model.Warehouse;
import co.posinvent.domain.repository.BatchRepository;
import co.posinvent.domain.repository.DesposteRepository;
import co.posinvent.domain.repository.ProductRepository;
import co.posinvent.domain.repository.StockRepository;
import co.posinvent.domain.repository.WarehouseRepository;
import co.posinvent.domain.service.ManualDesposteDomainService;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ManualDesposteUseCaseTest {

    @Mock
    private BatchRepository batchRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private WarehouseRepository warehouseRepository;

    @Mock
    private StockRepository stockRepository;
    @Mock
    private RecordMovementUseCase recordMovement;
    @Mock
    private DesposteRepository desposteRepository;

    private ManualDesposteUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new ManualDesposteUseCase(
                batchRepository,
                productRepository,
                warehouseRepository,
                stockRepository,
                new ManualDesposteDomainService(),
                recordMovement,
                desposteRepository
        );
    }

    @Test
    void processManual_createsAndUpdatesStockThenClosesSourceBatch() {
        var sourceBatchId = UUID.randomUUID();
        var parentProductId = UUID.randomUUID();
        var parentWarehouseId = UUID.randomUUID();
        var productA = UUID.randomUUID();
        var productB = UUID.randomUUID();
        var warehouseA = UUID.randomUUID();
        var warehouseB = UUID.randomUUID();

        var batch = new Batch(
                sourceBatchId,
                parentProductId,
                UUID.randomUUID(),
                parentWarehouseId,
                LocalDate.of(2026, 5, 13),
                new BigDecimal("100"),
                new BigDecimal("1000"),
                Batch.BatchStatus.OPEN,
                "Lote origen",
                null,
                UUID.randomUUID(),
                OffsetDateTime.now().minusDays(1),
                OffsetDateTime.now().minusHours(1),
                null,
                null,
                null, null, null, null, null, null, null
        );

        when(batchRepository.findById(sourceBatchId)).thenReturn(Optional.of(batch));
        when(productRepository.findById(productA)).thenReturn(Optional.of(product(productA)));
        when(productRepository.findById(productB)).thenReturn(Optional.of(product(productB)));
        when(warehouseRepository.findById(warehouseA)).thenReturn(Optional.of(warehouse(warehouseA)));
        when(warehouseRepository.findById(warehouseB)).thenReturn(Optional.of(warehouse(warehouseB)));

        // Stock de lotes hijos: sin stock previo.
        when(stockRepository.findByProductBatchWarehouse(any(), any(), any()))
                .thenReturn(Optional.empty());

        // Stock del lote padre: 100 unidades (suficiente para consumir). La primera
        // consulta (decremento) ve 100; la segunda (post-decremento) ve 0 → CLOSED.
        when(stockRepository.findByProductBatchWarehouse(parentProductId, sourceBatchId, parentWarehouseId))
                .thenReturn(
                        Optional.of(stock(parentProductId, sourceBatchId, parentWarehouseId, "100")),
                        Optional.of(stock(parentProductId, sourceBatchId, parentWarehouseId, "0"))
                );

        // Asigna ids generados a los lotes hijos (id == null al crearse).
        doAnswer(invocation -> {
            Batch b = invocation.getArgument(0);
            if (b.id() != null) {
                return b;
            }
            return new Batch(
                    UUID.randomUUID(), b.productId(), b.supplierId(), b.warehouseId(),
                    b.entryDate(), b.initialWeight(), b.purchaseCost(), b.status(), b.notes(),
                    b.expirationDate(), b.createdBy(), b.createdAt(), b.updatedAt(), b.updatedBy(),
                    b.sourceReceiptId(), b.ocId(), b.productName(), b.supplierName(), b.warehouseName(),
                    b.parentBatchId(), b.batchType(), b.unitOfMeasureId());
        }).when(batchRepository).save(any(Batch.class));
        doAnswer(invocation -> invocation.getArgument(0)).when(stockRepository).save(any(InventoryStock.class));

        var response = useCase.processManual(new ManualDesposteRequest(
                sourceBatchId,
                co.posinvent.domain.model.ManualDespostePlan.DesposteSourceType.MANUAL,
                "Slice 1 manual",
                new BigDecimal("4"),
                new BigDecimal("0.5"),
                "Primer slice",
                List.of(
                        new ManualDesposteRequest.ManualDesposteCutRequest(
                                productA,
                                warehouseA,
                                new BigDecimal("60"),
                                new BigDecimal("20"),
                                null
                        ),
                        new ManualDesposteRequest.ManualDesposteCutRequest(
                                productB,
                                warehouseB,
                                new BigDecimal("35"),
                                new BigDecimal("10"),
                                null
                        )
                )
        ));

        assertThat(response.sourceBatchId()).isEqualTo(sourceBatchId);
        assertThat(response.massBalance().withinTolerance()).isTrue();
        assertThat(response.totalAllocatedCost()).isEqualByComparingTo("1000.000000");
        assertThat(response.childBatchIds()).hasSize(2);

        var batchCaptor = ArgumentCaptor.forClass(Batch.class);
        verify(batchRepository, org.mockito.Mockito.times(3)).save(batchCaptor.capture());

        var children = batchCaptor.getAllValues().stream()
                .filter(b -> b.batchType() == BatchType.CHILD)
                .toList();
        assertThat(children).hasSize(2);
        assertThat(children).allMatch(b -> sourceBatchId.equals(b.parentBatchId()));

        var transition = batchCaptor.getAllValues().stream()
                .filter(b -> sourceBatchId.equals(b.id()))
                .findFirst()
                .orElseThrow();
        assertThat(transition.status()).isEqualTo(Batch.BatchStatus.CLOSED);

        // --- Persisted desposte snapshot (MVM) ---
        var desposteCaptor = ArgumentCaptor.forClass(Desposte.class);
        verify(desposteRepository).save(desposteCaptor.capture());
        var saved = desposteCaptor.getValue();

        assertThat(saved.id()).isNull();
        assertThat(saved.sourceBatchId()).isEqualTo(sourceBatchId);
        assertThat(saved.productId()).isEqualTo(parentProductId);
        assertThat(saved.warehouseId()).isEqualTo(parentWarehouseId);
        assertThat(saved.inputWeight()).isEqualByComparingTo("100");
        assertThat(saved.totalCutsWeight()).isEqualByComparingTo("95");
        assertThat(saved.wasteWeight()).isEqualByComparingTo("4");
        assertThat(saved.shrinkWeight()).isEqualByComparingTo("0.5");
        assertThat(saved.withinTolerance()).isTrue();
        assertThat(saved.yieldPercentage()).isEqualByComparingTo("95.0000");
        assertThat(saved.totalCommercialValue()).isEqualByComparingTo("1550");
        assertThat(saved.totalAllocatedCost()).isEqualByComparingTo("1000");
        assertThat(saved.notes()).isEqualTo("Primer slice");
        assertThat(saved.createdBy()).isEqualTo(batch.createdBy().toString());
        assertThat(saved.createdAt()).isNull();

        assertThat(saved.cuts()).hasSize(2);
        assertThat(saved.cuts().get(0).productId()).isEqualTo(productA);
        assertThat(saved.cuts().get(0).warehouseId()).isEqualTo(warehouseA);
        assertThat(saved.cuts().get(0).weight()).isEqualByComparingTo("60");
        assertThat(saved.cuts().get(0).childBatchId()).isNotNull();
        assertThat(saved.cuts().get(1).productId()).isEqualTo(productB);
        assertThat(saved.cuts().get(1).warehouseId()).isEqualTo(warehouseB);
        assertThat(saved.cuts().get(1).weight()).isEqualByComparingTo("35");
        assertThat(saved.cuts().get(1).childBatchId()).isNotNull();
    }

    private Product product(UUID id) {
        return new Product(
                id,
                "P-" + id,
                "Producto " + id,
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
                new BigDecimal("10"),
                "EXENTO",
                new BigDecimal("1.1000"),
                "PEPS",
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
                OffsetDateTime.now().minusDays(2),
                OffsetDateTime.now().minusDays(1),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of()
        );
    }

    private Warehouse warehouse(UUID id) {
        return new Warehouse(
                id,
                "Bodega " + id,
                "Ubicacion",
                Warehouse.WarehouseType.CORTES,
                true,
                OffsetDateTime.now().minusDays(3)
        );
    }

    private InventoryStock stock(UUID productId, UUID batchId, UUID warehouseId, String quantity) {
        return new InventoryStock(
                UUID.randomUUID(),
                productId,
                batchId,
                warehouseId,
                new BigDecimal(quantity),
                BigDecimal.ZERO,
                new BigDecimal("10.000000"),
                OffsetDateTime.now().minusDays(1),
                OffsetDateTime.now().minusHours(2)
        );
    }
}
