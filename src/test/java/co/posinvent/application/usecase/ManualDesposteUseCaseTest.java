package co.posinvent.application.usecase;

import co.posinvent.application.dto.ManualDesposteRequest;
import co.posinvent.domain.model.Batch;
import co.posinvent.domain.model.InventoryStock;
import co.posinvent.domain.model.Product;
import co.posinvent.domain.model.Warehouse;
import co.posinvent.domain.repository.BatchRepository;
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

    private ManualDesposteUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new ManualDesposteUseCase(
                batchRepository,
                productRepository,
                warehouseRepository,
                stockRepository,
                new ManualDesposteDomainService(),
                recordMovement
        );
    }

    @Test
    void processManual_createsAndUpdatesStockThenClosesSourceBatch() {
        var sourceBatchId = UUID.randomUUID();
        var productA = UUID.randomUUID();
        var productB = UUID.randomUUID();
        var warehouseA = UUID.randomUUID();
        var warehouseB = UUID.randomUUID();

        var batch = new Batch(
                sourceBatchId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
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
                null, null, null
        );

        when(batchRepository.findById(sourceBatchId)).thenReturn(Optional.of(batch));
        when(productRepository.findById(productA)).thenReturn(Optional.of(product(productA)));
        when(productRepository.findById(productB)).thenReturn(Optional.of(product(productB)));
        when(warehouseRepository.findById(warehouseA)).thenReturn(Optional.of(warehouse(warehouseA)));
        when(warehouseRepository.findById(warehouseB)).thenReturn(Optional.of(warehouse(warehouseB)));
        when(stockRepository.findByProductBatchWarehouse(productA, sourceBatchId, warehouseA))
                .thenReturn(Optional.of(new InventoryStock(
                        UUID.randomUUID(),
                        productA,
                        sourceBatchId,
                        warehouseA,
                        new BigDecimal("10"),
                        BigDecimal.ZERO,
                        new BigDecimal("5.000000"),
                        OffsetDateTime.now().minusDays(1),
                        OffsetDateTime.now().minusHours(2)
                )));
        when(stockRepository.findByProductBatchWarehouse(productB, sourceBatchId, warehouseB))
                .thenReturn(Optional.empty());
        doAnswer(invocation -> invocation.getArgument(0)).when(stockRepository).save(any(InventoryStock.class));
        doAnswer(invocation -> invocation.getArgument(0)).when(batchRepository).save(any(Batch.class));

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
                                new BigDecimal("20")
                        ),
                        new ManualDesposteRequest.ManualDesposteCutRequest(
                                productB,
                                warehouseB,
                                new BigDecimal("35"),
                                new BigDecimal("10")
                        )
                )
        ));

        var stockCaptor = ArgumentCaptor.forClass(InventoryStock.class);
        verify(stockRepository, org.mockito.Mockito.times(2)).save(stockCaptor.capture());

        var savedStocks = stockCaptor.getAllValues();
        assertThat(savedStocks.get(0).currentQuantity()).isEqualByComparingTo("70");
        assertThat(savedStocks.get(0).unitCost()).isEqualByComparingTo("11.774194");
        assertThat(savedStocks.get(1).id()).isNull();
        assertThat(savedStocks.get(1).currentQuantity()).isEqualByComparingTo("35.000000");
        assertThat(savedStocks.get(1).unitCost()).isEqualByComparingTo("6.451613");

        var batchCaptor = ArgumentCaptor.forClass(Batch.class);
        verify(batchRepository).save(batchCaptor.capture());
        assertThat(batchCaptor.getValue().status()).isEqualTo(Batch.BatchStatus.CLOSED);

        assertThat(response.sourceBatchId()).isEqualTo(sourceBatchId);
        assertThat(response.massBalance().withinTolerance()).isTrue();
        assertThat(response.totalAllocatedCost()).isEqualByComparingTo("1000.000000");
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
}
