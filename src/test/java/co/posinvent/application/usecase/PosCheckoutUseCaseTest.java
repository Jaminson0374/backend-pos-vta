package co.posinvent.application.usecase;

import co.posinvent.domain.model.*;
import co.posinvent.domain.repository.*;
import co.posinvent.domain.service.FefoPicker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PosCheckoutUseCaseTest {

    @Mock private SalesDocumentRepository documentRepo;
    @Mock private SaleItemRepository itemRepo;
    @Mock private StockRepository stockRepo;
    @Mock private ProductRepository productRepo;
    @Mock private PriceEngineService priceEngine;
    @Mock private FefoPicker fefoPicker;
    @Mock private RecordMovementUseCase recordMovement;
    @Mock private CostingService costingService;
    @Mock private ApplicationEventPublisher eventPublisher;

    private PosCheckoutUseCase useCase;

    private static final UUID ORDER_ID = UUID.randomUUID();
    private static final UUID PRODUCT_ID = UUID.randomUUID();
    private static final UUID CLIENT_ID = UUID.randomUUID();
    private static final UUID WAREHOUSE_ID = UUID.randomUUID();
    private static final UUID BATCH1_ID = UUID.randomUUID();
    private static final UUID BATCH2_ID = UUID.randomUUID();
    private static final UUID STOCK1_ID = UUID.randomUUID();
    private static final UUID STOCK2_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID CASH_REGISTER_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        useCase = new PosCheckoutUseCase(
                documentRepo, itemRepo, stockRepo, productRepo,
                priceEngine, fefoPicker, recordMovement, costingService,
                eventPublisher
        );
    }

    @Test
    void shouldCreateMultipleInventoryMovementsWhenFefoPicksMultipleBatches() {
        // ── Arrange ─────────────────────────────────────────────────────
        var item = new SaleItem(null, ORDER_ID, PRODUCT_ID,
                new BigDecimal("8"), null, null, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, 1, null);
        var order = new SalesDocument(
                ORDER_ID, SalesDocumentType.ORDER, SalesDocumentStatus.CONFIRMED,
                "ORD-001", CLIENT_ID, WAREHOUSE_ID, null, null, null,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, USER_ID,
                OffsetDateTime.now(), OffsetDateTime.now(),
                List.of(item), null, false, null
        );

        // documentRepo.findById handles both order and invoice lookups
        when(documentRepo.findById(any(UUID.class))).thenAnswer(inv -> {
            UUID id = inv.getArgument(0);
            if (id.equals(ORDER_ID)) {
                return Optional.of(order);
            }
            return Optional.of(new SalesDocument(
                    id, SalesDocumentType.INVOICE, SalesDocumentStatus.DRAFT,
                    "INV-001", CLIENT_ID, WAREHOUSE_ID, null, CASH_REGISTER_ID, ORDER_ID,
                    new BigDecimal("400000"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    new BigDecimal("400000"), USER_ID,
                    OffsetDateTime.now(), OffsetDateTime.now(),
                    List.of(), null, false, null
            ));
        });

        // Price engine returns a simple price
        when(priceEngine.resolvePrice(PRODUCT_ID, CLIENT_ID))
                .thenReturn(new PriceResult(new BigDecimal("50000"), "EXENTO", BigDecimal.ZERO, BigDecimal.ZERO));

        // FEFO returns 2 batches
        var alloc1 = new BatchAllocation(BATCH1_ID, new BigDecimal("3"), new BigDecimal("100.000000"));
        var alloc2 = new BatchAllocation(BATCH2_ID, new BigDecimal("5"), new BigDecimal("110.000000"));
        when(fefoPicker.pick(PRODUCT_ID, WAREHOUSE_ID, new BigDecimal("8")))
                .thenReturn(List.of(alloc1, alloc2));

        // Stock for batch 1
        when(stockRepo.findByProductBatchWarehouse(PRODUCT_ID, BATCH1_ID, WAREHOUSE_ID))
                .thenReturn(Optional.of(new InventoryStock(
                        STOCK1_ID, PRODUCT_ID, BATCH1_ID, WAREHOUSE_ID,
                        new BigDecimal("10"), BigDecimal.ZERO, new BigDecimal("100.000000"),
                        OffsetDateTime.now().minusDays(1), null
                )));

        // Stock for batch 2
        when(stockRepo.findByProductBatchWarehouse(PRODUCT_ID, BATCH2_ID, WAREHOUSE_ID))
                .thenReturn(Optional.of(new InventoryStock(
                        STOCK2_ID, PRODUCT_ID, BATCH2_ID, WAREHOUSE_ID,
                        new BigDecimal("20"), BigDecimal.ZERO, new BigDecimal("110.000000"),
                        OffsetDateTime.now().minusDays(1), null
                )));

        // Stock save returns whatever is passed
        doAnswer(inv -> inv.getArgument(0)).when(stockRepo).save(any(InventoryStock.class));

        // Record movement
        when(recordMovement.record(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new InventoryMovement(
                        UUID.randomUUID(), PRODUCT_ID, null, WAREHOUSE_ID,
                        MovementType.EXIT, BigDecimal.ONE, BigDecimal.ZERO,
                        BigDecimal.TEN, new BigDecimal("9"),
                        "SALE", ORDER_ID, "test", "SYSTEM", null
                ));

        // Costing service — product with PROMEDIO_PONDERADO, layers empty → no-op
        doReturn(BigDecimal.ZERO).when(costingService).resolveCostOnExit(any(), any(), any(), any());

        // productRepo.recalculateTotalStock
        doNothing().when(productRepo).recalculateTotalStock(PRODUCT_ID);

        // Invoice + order saves
        doAnswer(inv -> {
            SalesDocument doc = inv.getArgument(0);
            return new SalesDocument(
                    UUID.randomUUID(), doc.type(), doc.status(), doc.documentNumber(),
                    doc.clientId(), doc.warehouseId(), doc.shiftId(), doc.cashRegisterId(), doc.sourceDocumentId(),
                    doc.totalNet(), doc.totalTax0(), doc.totalTax5(), doc.totalTax8(), doc.totalTax19(),
                    doc.totalAmount(), doc.createdBy(), doc.createdAt(), doc.updatedAt(),
                    doc.items(), doc.dueDate(), doc.isCreditSale(), doc.reason()
            );
        }).when(documentRepo).save(any(SalesDocument.class));

        doReturn(new SaleItem(UUID.randomUUID(), null, PRODUCT_ID,
                BigDecimal.ONE, null, null, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0, null))
                .when(itemRepo).save(any(SaleItem.class));

        // ── Act ─────────────────────────────────────────────────────────
        var request = new CheckoutRequest(ORDER_ID, CASH_REGISTER_ID,
                List.of(new CheckoutRequest.PaymentLine("CASH", new BigDecimal("400000"))));
        useCase.checkout(request, USER_ID);

        // ── Assert ──────────────────────────────────────────────────────

        // stockRepo.save called twice (one per batch)
        var stockCaptor = ArgumentCaptor.forClass(InventoryStock.class);
        verify(stockRepo, times(2)).save(stockCaptor.capture());
        var savedStocks = stockCaptor.getAllValues();
        // Batch 1: 10 - 3 = 7
        assertThat(savedStocks.get(0).currentQuantity()).isEqualByComparingTo("7");
        assertThat(savedStocks.get(0).batchId()).isEqualTo(BATCH1_ID);
        // Batch 2: 20 - 5 = 15
        assertThat(savedStocks.get(1).currentQuantity()).isEqualByComparingTo("15");
        assertThat(savedStocks.get(1).batchId()).isEqualTo(BATCH2_ID);

        // recordMovement.record called twice with correct batchIds
        verify(recordMovement, times(2)).record(
                eq(PRODUCT_ID), any(), eq(WAREHOUSE_ID),
                eq(MovementType.EXIT), any(), any(), any(), any(),
                eq("SALE"), eq(ORDER_ID), any()
        );
    }
}
