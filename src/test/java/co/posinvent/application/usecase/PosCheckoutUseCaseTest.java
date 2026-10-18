package co.posinvent.application.usecase;

import co.posinvent.domain.exception.BusinessException;
import co.posinvent.domain.exception.ResourceNotFoundException;
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
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PosCheckoutUseCaseTest {

    @Mock private SalesDocumentRepository documentRepo;
    @Mock private SalePaymentRepository salePaymentRepo;
    @Mock private StockRepository stockRepo;
    @Mock private ProductRepository productRepo;
    @Mock private PriceEngineService priceEngine;
    @Mock private FefoPicker fefoPicker;
    @Mock private RecordMovementUseCase recordMovement;
    @Mock private CostingOrchestrator costingService;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private OptimisticConcurrencyExecutor concurrencyExecutor;
    @Mock private DianNumberingService numberingService;
    @Mock private CompanyConfigRepository companyConfigRepo;
    @Mock private UserRepository userRepo;
    @Mock private AuthorizeDiscountUseCase authorizeDiscountUseCase;
    @Mock private ShiftRepository shiftRepo;

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
    private static final UUID INVOICE_ID = UUID.randomUUID();
    private static final UUID SHIFT_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        // The executor owns the transaction boundary; in this unit test it just
        // runs the supplier inline so the existing assertions remain meaningful.
        when(concurrencyExecutor.execute(any())).thenAnswer(inv ->
                ((Supplier<?>) inv.getArgument(0)).get());

        useCase = new PosCheckoutUseCase(
                documentRepo, salePaymentRepo, stockRepo, productRepo,
                priceEngine, fefoPicker, recordMovement, costingService,
                eventPublisher, concurrencyExecutor, numberingService,
                new DiscountEngineService(productRepo, companyConfigRepo), userRepo,
                authorizeDiscountUseCase, shiftRepo
        );

        // Numbering is delegated to the active DIAN resolution service; its own
        // behavior is covered by DianNumberingServiceTest. Lenient because failure-path
        // tests stop before numbering is reached.
        lenient().when(numberingService.nextInvoiceNumber()).thenReturn("FE0001");
    }

    private static Shift openShift(UUID ownerId) {
        return new Shift(
                SHIFT_ID, CASH_REGISTER_ID, ownerId, null, null,
                OffsetDateTime.now().minusHours(1), null,
                BigDecimal.ZERO, null, ShiftStatus.OPEN, null, OffsetDateTime.now());
    }

    private void stubOpenShift() {
        when(shiftRepo.findById(SHIFT_ID)).thenReturn(Optional.of(openShift(USER_ID)));
    }

    private static SalesDocument confirmedOrder(UUID orderShiftId) {
        var item = new SaleItem(null, ORDER_ID, PRODUCT_ID,
                BigDecimal.ONE, null, null, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, 1, null);
        return new SalesDocument(
                ORDER_ID, SalesDocumentType.ORDER, SalesDocumentStatus.CONFIRMED,
                "ORD-TEST", CLIENT_ID, WAREHOUSE_ID, orderShiftId, null, null,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, USER_ID,
                OffsetDateTime.now(), OffsetDateTime.now(),
                List.of(item), null, false, null
        );
    }

    private static User userWithRole(String roleName) {
        return new User(USER_ID, "test-user", "Test User", "test@example.com",
                new Role(UUID.randomUUID(), roleName, null), true,
                OffsetDateTime.now(), OffsetDateTime.now(), null, null);
    }

    @Test
    void shouldCreateMultipleInventoryMovementsWhenFefoPicksMultipleBatches() {
        // ── Arrange ─────────────────────────────────────────────────────
        stubOpenShift();
        var item = new SaleItem(null, ORDER_ID, PRODUCT_ID,
                new BigDecimal("8"), null, null, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, 1, null);
        var order = new SalesDocument(
                ORDER_ID, SalesDocumentType.ORDER, SalesDocumentStatus.CONFIRMED,
                "ORD-001", CLIENT_ID, WAREHOUSE_ID, null, null, null,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
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
                    new BigDecimal("400000"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
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

        // Invoice + order saves. The invoice gets a deterministic id so the
        // sale-payment assertions can verify the FK link.
        doAnswer(inv -> {
            SalesDocument doc = inv.getArgument(0);
            var assignedId = doc.type() == SalesDocumentType.INVOICE
                    ? INVOICE_ID : UUID.randomUUID();
            return new SalesDocument(
                    assignedId, doc.type(), doc.status(), doc.documentNumber(),
                    doc.clientId(), doc.warehouseId(), doc.shiftId(), doc.cashRegisterId(), doc.sourceDocumentId(),
                    doc.totalNet(), doc.totalTax0(), doc.totalTax5(), doc.totalTax19(),
                    doc.totalAmount(), doc.createdBy(), doc.createdAt(), doc.updatedAt(),
                    doc.items(), doc.dueDate(), doc.isCreditSale(), doc.reason()
            );
        }).when(documentRepo).save(any(SalesDocument.class));

        // ── Act ─────────────────────────────────────────────────────────
        var request = new CheckoutRequest(ORDER_ID, CASH_REGISTER_ID, SHIFT_ID,
                List.of(
                        new CheckoutRequest.PaymentLine("CASH", new BigDecimal("250000")),
                        new CheckoutRequest.PaymentLine("CARD", new BigDecimal("150000"))));
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

        // One sale_payments row per payment line, linked to the saved invoice.
        var paymentCaptor = ArgumentCaptor.forClass(SalePayment.class);
        verify(salePaymentRepo, times(2)).save(paymentCaptor.capture());
        var savedPayments = paymentCaptor.getAllValues();
        assertThat(savedPayments.get(0).salesDocumentId()).isEqualTo(INVOICE_ID);
        assertThat(savedPayments.get(0).method()).isEqualTo("CASH");
        assertThat(savedPayments.get(0).amount()).isEqualByComparingTo("250000");
        assertThat(savedPayments.get(0).createdBy()).isEqualTo(USER_ID);
        assertThat(savedPayments.get(1).salesDocumentId()).isEqualTo(INVOICE_ID);
        assertThat(savedPayments.get(1).method()).isEqualTo("CARD");
        assertThat(savedPayments.get(1).amount()).isEqualByComparingTo("150000");
        assertThat(savedPayments.get(1).createdBy()).isEqualTo(USER_ID);

        // Both documents are stamped with the validated shift and its register.
        var docCaptor = ArgumentCaptor.forClass(SalesDocument.class);
        verify(documentRepo, times(2)).save(docCaptor.capture());
        var savedInvoiceDoc = docCaptor.getAllValues().get(0);
        var invoicedOrder = docCaptor.getAllValues().get(1);
        assertThat(savedInvoiceDoc.type()).isEqualTo(SalesDocumentType.INVOICE);
        assertThat(savedInvoiceDoc.shiftId()).isEqualTo(SHIFT_ID);
        assertThat(savedInvoiceDoc.cashRegisterId()).isEqualTo(CASH_REGISTER_ID);
        assertThat(invoicedOrder.type()).isEqualTo(SalesDocumentType.ORDER);
        assertThat(invoicedOrder.shiftId()).isEqualTo(SHIFT_ID);
        assertThat(invoicedOrder.cashRegisterId()).isEqualTo(CASH_REGISTER_ID);
    }

    /**
     * Audit A1: the checkout must consume the reservation booked on CONFIRMED, so
     * the saved stock reduces {@code committedQuantity} by the allocation and never
     * goes below zero for an unreserved order.
     */
    @Test
    void shouldReduceCommittedQuantityByAllocationAndClampAtZero() {
        // ── Arrange ─────────────────────────────────────────────────────
        stubOpenShift();
        var item = new SaleItem(null, ORDER_ID, PRODUCT_ID,
                new BigDecimal("8"), null, null, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, 1, null);
        var order = new SalesDocument(
                ORDER_ID, SalesDocumentType.ORDER, SalesDocumentStatus.CONFIRMED,
                "ORD-002", CLIENT_ID, WAREHOUSE_ID, null, null, null,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, USER_ID,
                OffsetDateTime.now(), OffsetDateTime.now(),
                List.of(item), null, false, null
        );

        when(documentRepo.findById(any(UUID.class))).thenAnswer(inv -> {
            UUID id = inv.getArgument(0);
            if (id.equals(ORDER_ID)) {
                return Optional.of(order);
            }
            return Optional.of(new SalesDocument(
                    id, SalesDocumentType.INVOICE, SalesDocumentStatus.DRAFT,
                    "INV-002", CLIENT_ID, WAREHOUSE_ID, null, CASH_REGISTER_ID, ORDER_ID,
                    new BigDecimal("400000"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    new BigDecimal("400000"), USER_ID,
                    OffsetDateTime.now(), OffsetDateTime.now(),
                    List.of(), null, false, null
            ));
        });

        when(priceEngine.resolvePrice(PRODUCT_ID, CLIENT_ID))
                .thenReturn(new PriceResult(new BigDecimal("50000"), "EXENTO", BigDecimal.ZERO, BigDecimal.ZERO));

        var alloc1 = new BatchAllocation(BATCH1_ID, new BigDecimal("3"), new BigDecimal("100.000000"));
        var alloc2 = new BatchAllocation(BATCH2_ID, new BigDecimal("5"), new BigDecimal("110.000000"));
        when(fefoPicker.pick(PRODUCT_ID, WAREHOUSE_ID, new BigDecimal("8")))
                .thenReturn(List.of(alloc1, alloc2));

        // Batch 1 is reserved (committed=8); batch 2 has no reservation (committed=0).
        when(stockRepo.findByProductBatchWarehouse(PRODUCT_ID, BATCH1_ID, WAREHOUSE_ID))
                .thenReturn(Optional.of(new InventoryStock(
                        STOCK1_ID, PRODUCT_ID, BATCH1_ID, WAREHOUSE_ID,
                        new BigDecimal("10"), new BigDecimal("8"), new BigDecimal("100.000000"),
                        OffsetDateTime.now().minusDays(1), null
                )));
        when(stockRepo.findByProductBatchWarehouse(PRODUCT_ID, BATCH2_ID, WAREHOUSE_ID))
                .thenReturn(Optional.of(new InventoryStock(
                        STOCK2_ID, PRODUCT_ID, BATCH2_ID, WAREHOUSE_ID,
                        new BigDecimal("20"), BigDecimal.ZERO, new BigDecimal("110.000000"),
                        OffsetDateTime.now().minusDays(1), null
                )));

        doAnswer(inv -> inv.getArgument(0)).when(stockRepo).save(any(InventoryStock.class));

        when(recordMovement.record(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new InventoryMovement(
                        UUID.randomUUID(), PRODUCT_ID, null, WAREHOUSE_ID,
                        MovementType.EXIT, BigDecimal.ONE, BigDecimal.ZERO,
                        BigDecimal.TEN, new BigDecimal("9"),
                        "SALE", ORDER_ID, "test", "SYSTEM", null
                ));

        doReturn(BigDecimal.ZERO).when(costingService).resolveCostOnExit(any(), any(), any(), any());
        doNothing().when(productRepo).recalculateTotalStock(PRODUCT_ID);

        doAnswer(inv -> {
            SalesDocument doc = inv.getArgument(0);
            var assignedId = doc.type() == SalesDocumentType.INVOICE
                    ? INVOICE_ID : UUID.randomUUID();
            return new SalesDocument(
                    assignedId, doc.type(), doc.status(), doc.documentNumber(),
                    doc.clientId(), doc.warehouseId(), doc.shiftId(), doc.cashRegisterId(), doc.sourceDocumentId(),
                    doc.totalNet(), doc.totalTax0(), doc.totalTax5(), doc.totalTax19(),
                    doc.totalAmount(), doc.createdBy(), doc.createdAt(), doc.updatedAt(),
                    doc.items(), doc.dueDate(), doc.isCreditSale(), doc.reason()
            );
        }).when(documentRepo).save(any(SalesDocument.class));

        // ── Act ─────────────────────────────────────────────────────────
        var request = new CheckoutRequest(ORDER_ID, CASH_REGISTER_ID, SHIFT_ID,
                List.of(new CheckoutRequest.PaymentLine("CASH", new BigDecimal("400000"))));
        useCase.checkout(request, USER_ID);

        // ── Assert ──────────────────────────────────────────────────────
        var stockCaptor = ArgumentCaptor.forClass(InventoryStock.class);
        verify(stockRepo, times(2)).save(stockCaptor.capture());
        var savedStocks = stockCaptor.getAllValues();
        // Batch 1: committed 8 - 3 = 5
        assertThat(savedStocks.get(0).committedQuantity()).isEqualByComparingTo("5");
        // Batch 2: committed 0 - 5 clamped at 0
        assertThat(savedStocks.get(1).committedQuantity()).isEqualByComparingTo("0");
    }

    @Test
    void shouldRejectCheckoutWhenShiftDoesNotExist() {
        when(documentRepo.findById(ORDER_ID)).thenReturn(Optional.of(confirmedOrder(null)));
        when(shiftRepo.findById(SHIFT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.checkout(
                new CheckoutRequest(ORDER_ID, CASH_REGISTER_ID, SHIFT_ID,
                        List.of(new CheckoutRequest.PaymentLine("CASH", BigDecimal.TEN))),
                USER_ID))
                .isInstanceOf(ResourceNotFoundException.class);

        verifyNoInteractions(stockRepo);
        verify(documentRepo, never()).save(any());
    }

    @Test
    void shouldRejectCheckoutWhenShiftIsClosed() {
        when(documentRepo.findById(ORDER_ID)).thenReturn(Optional.of(confirmedOrder(null)));
        when(shiftRepo.findById(SHIFT_ID)).thenReturn(Optional.of(new Shift(
                SHIFT_ID, CASH_REGISTER_ID, USER_ID, null, null,
                OffsetDateTime.now().minusHours(2), OffsetDateTime.now(),
                BigDecimal.ZERO, BigDecimal.ZERO, ShiftStatus.CLOSED, null, OffsetDateTime.now())));

        assertThatThrownBy(() -> useCase.checkout(
                new CheckoutRequest(ORDER_ID, CASH_REGISTER_ID, SHIFT_ID,
                        List.of(new CheckoutRequest.PaymentLine("CASH", BigDecimal.TEN))),
                USER_ID))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo("POS_SHIFT_NOT_OPEN"));

        verifyNoInteractions(stockRepo);
        verify(documentRepo, never()).save(any());
    }

    @Test
    void shouldDenyCheckoutWhenShiftBelongsToAnotherCashier() {
        when(documentRepo.findById(ORDER_ID)).thenReturn(Optional.of(confirmedOrder(null)));
        when(shiftRepo.findById(SHIFT_ID))
                .thenReturn(Optional.of(openShift(UUID.randomUUID())));
        when(userRepo.findById(USER_ID)).thenReturn(Optional.of(userWithRole("CAJERO")));

        assertThatThrownBy(() -> useCase.checkout(
                new CheckoutRequest(ORDER_ID, CASH_REGISTER_ID, SHIFT_ID,
                        List.of(new CheckoutRequest.PaymentLine("CASH", BigDecimal.TEN))),
                USER_ID))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(stockRepo);
        verify(documentRepo, never()).save(any());
    }

    @Test
    void shouldAllowAdminToCheckoutAShiftOwnedByAnotherUser() {
        when(shiftRepo.findById(SHIFT_ID))
                .thenReturn(Optional.of(openShift(UUID.randomUUID())));
        when(userRepo.findById(USER_ID)).thenReturn(Optional.of(userWithRole("ADMIN")));
        stubSuccessfulCheckout();

        assertThatCode(() -> useCase.checkout(
                new CheckoutRequest(ORDER_ID, CASH_REGISTER_ID, SHIFT_ID,
                        List.of(new CheckoutRequest.PaymentLine("CASH", new BigDecimal("1000.00")))),
                USER_ID))
                .doesNotThrowAnyException();

        verify(stockRepo).save(any(InventoryStock.class));
    }

    @Test
    void shouldRejectCheckoutWhenRequestRegisterDiffersFromTheShift() {
        when(documentRepo.findById(ORDER_ID)).thenReturn(Optional.of(confirmedOrder(null)));
        when(shiftRepo.findById(SHIFT_ID)).thenReturn(Optional.of(openShift(USER_ID)));

        assertThatThrownBy(() -> useCase.checkout(
                new CheckoutRequest(ORDER_ID, UUID.randomUUID(), SHIFT_ID,
                        List.of(new CheckoutRequest.PaymentLine("CASH", BigDecimal.TEN))),
                USER_ID))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo("POS_SHIFT_REGISTER_MISMATCH"));

        verifyNoInteractions(stockRepo);
        verify(documentRepo, never()).save(any());
    }

    @Test
    void shouldRejectCheckoutWhenOrderBelongsToAnotherShift() {
        when(documentRepo.findById(ORDER_ID))
                .thenReturn(Optional.of(confirmedOrder(UUID.randomUUID())));
        when(shiftRepo.findById(SHIFT_ID)).thenReturn(Optional.of(openShift(USER_ID)));

        assertThatThrownBy(() -> useCase.checkout(
                new CheckoutRequest(ORDER_ID, CASH_REGISTER_ID, SHIFT_ID,
                        List.of(new CheckoutRequest.PaymentLine("CASH", BigDecimal.TEN))),
                USER_ID))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo("POS_SHIFT_MISMATCH"));

        verifyNoInteractions(stockRepo);
        verify(documentRepo, never()).save(any());
    }

    private void stubSuccessfulCheckout() {
        when(documentRepo.findById(any(UUID.class))).thenAnswer(inv -> {
            UUID id = inv.getArgument(0);
            if (id.equals(ORDER_ID)) {
                return Optional.of(confirmedOrder(null));
            }
            return Optional.of(new SalesDocument(
                    id, SalesDocumentType.INVOICE, SalesDocumentStatus.DRAFT,
                    "INV-TEST", CLIENT_ID, WAREHOUSE_ID, SHIFT_ID, CASH_REGISTER_ID, ORDER_ID,
                    new BigDecimal("1000.00"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    new BigDecimal("1000.00"), USER_ID,
                    OffsetDateTime.now(), OffsetDateTime.now(),
                    List.of(), null, false, null
            ));
        });

        when(priceEngine.resolvePrice(PRODUCT_ID, CLIENT_ID))
                .thenReturn(new PriceResult(new BigDecimal("1000.00"), "EXENTO", BigDecimal.ZERO, BigDecimal.ZERO));
        when(fefoPicker.pick(PRODUCT_ID, WAREHOUSE_ID, BigDecimal.ONE))
                .thenReturn(List.of(new BatchAllocation(BATCH1_ID, BigDecimal.ONE, BigDecimal.ZERO)));
        when(stockRepo.findByProductBatchWarehouse(PRODUCT_ID, BATCH1_ID, WAREHOUSE_ID))
                .thenReturn(Optional.of(new InventoryStock(
                        STOCK1_ID, PRODUCT_ID, BATCH1_ID, WAREHOUSE_ID,
                        new BigDecimal("10"), BigDecimal.ZERO, BigDecimal.ZERO,
                        OffsetDateTime.now().minusDays(1), null
                )));
        doAnswer(inv -> inv.getArgument(0)).when(stockRepo).save(any(InventoryStock.class));
        when(recordMovement.record(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new InventoryMovement(
                        UUID.randomUUID(), PRODUCT_ID, null, WAREHOUSE_ID,
                        MovementType.EXIT, BigDecimal.ONE, BigDecimal.ZERO,
                        BigDecimal.TEN, new BigDecimal("9"),
                        "SALE", ORDER_ID, "test", "SYSTEM", null
                ));
        doReturn(BigDecimal.ZERO).when(costingService).resolveCostOnExit(any(), any(), any(), any());
        doNothing().when(productRepo).recalculateTotalStock(PRODUCT_ID);
        doAnswer(inv -> {
            SalesDocument doc = inv.getArgument(0);
            var assignedId = doc.type() == SalesDocumentType.INVOICE
                    ? INVOICE_ID : UUID.randomUUID();
            return new SalesDocument(
                    assignedId, doc.type(), doc.status(), doc.documentNumber(),
                    doc.clientId(), doc.warehouseId(), doc.shiftId(), doc.cashRegisterId(), doc.sourceDocumentId(),
                    doc.totalNet(), doc.totalTax0(), doc.totalTax5(), doc.totalTax19(),
                    doc.totalAmount(), doc.createdBy(), doc.createdAt(), doc.updatedAt(),
                    doc.items(), doc.dueDate(), doc.isCreditSale(), doc.reason()
            );
        }).when(documentRepo).save(any(SalesDocument.class));
    }
}
