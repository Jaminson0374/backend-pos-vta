package co.posinvent.application.usecase;

import co.posinvent.application.dto.DevolutionRequest;
import co.posinvent.application.dto.DevolutionResponse;
import co.posinvent.domain.exception.BusinessException;
import co.posinvent.domain.exception.ResourceNotFoundException;
import co.posinvent.domain.model.*;
import co.posinvent.domain.repository.AccountsReceivableRepository;
import co.posinvent.domain.repository.ProductRepository;
import co.posinvent.domain.repository.SaleItemRepository;
import co.posinvent.domain.repository.SalesDocumentRepository;
import co.posinvent.domain.repository.StockRepository;
import co.posinvent.domain.repository.ThirdPartyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PosDevolutionUseCaseTest {

    @Mock private SalesDocumentRepository documentRepo;
    @Mock private SaleItemRepository itemRepo;
    @Mock private StockRepository stockRepo;
    @Mock private ProductRepository productRepo;
    @Mock private RecordMovementUseCase recordMovement;
    @Mock private AccountsReceivableRepository arRepo;
    @Mock private ThirdPartyRepository thirdPartyRepo;
    @Mock private ApplicationEventPublisher eventPublisher;

    private PosDevolutionUseCase useCase;

    private static final UUID INVOICE_ID = UUID.randomUUID();
    private static final UUID PRODUCT_A_ID = UUID.randomUUID();
    private static final UUID PRODUCT_B_ID = UUID.randomUUID();
    private static final UUID PRODUCT_C_ID = UUID.randomUUID();
    private static final UUID PRODUCT_D_ID = UUID.randomUUID();
    private static final UUID BATCH_ID = UUID.randomUUID();
    private static final UUID WAREHOUSE_ID = UUID.randomUUID();
    private static final UUID CLIENT_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID STOCK_ID = UUID.randomUUID();

    private static final BigDecimal PRICE_10000 = new BigDecimal("10000.00");
    private static final BigDecimal TAX_RATE_19 = new BigDecimal("19");
    private static final BigDecimal QTY_5 = new BigDecimal("5");
    private static final BigDecimal QTY_3 = new BigDecimal("3");
    private static final BigDecimal QTY_7 = new BigDecimal("7");

    @BeforeEach
    void setUp() {
        useCase = new PosDevolutionUseCase(
                documentRepo, itemRepo, stockRepo, productRepo,
                recordMovement, arRepo, thirdPartyRepo, eventPublisher);
    }

    // ── Scenario 1: Invoice not found → ResourceNotFoundException ──────────

    @Test
    void invoiceNotFound_throwsResourceNotFoundException() {
        when(documentRepo.findById(INVOICE_ID)).thenReturn(Optional.empty());

        var request = new DevolutionRequest(INVOICE_ID,
                List.of(item(PRODUCT_A_ID, BigDecimal.ONE)), "Razón");

        assertThatThrownBy(() -> useCase.processDevolution(request, USER_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("no encontrado");

        verify(documentRepo).findById(INVOICE_ID);
        verifyNoMoreInteractions(documentRepo);
    }

    // ── Scenario 2: Invoice is ORDER not INVOICE → BusinessException ───────

    @Test
    void invoiceIsOrder_throwsBusinessException() {
        var order = document(SalesDocumentType.ORDER, SalesDocumentStatus.ISSUED, false, List.of());
        when(documentRepo.findById(INVOICE_ID)).thenReturn(Optional.of(order));

        var request = new DevolutionRequest(INVOICE_ID,
                List.of(item(PRODUCT_A_ID, BigDecimal.ONE)), "Razón");

        assertThatThrownBy(() -> useCase.processDevolution(request, USER_ID))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    var be = (BusinessException) ex;
                    assertThat(be.getErrorCode()).isEqualTo("DEV_NOT_INVOICE");
                    assertThat(be.getMessage()).contains("no es una factura");
                });
    }

    // ── Scenario 3: Invoice not ISSUED → BusinessException ──────────────────

    @Test
    void invoiceNotIssued_throwsBusinessException() {
        var draftInvoice = document(SalesDocumentType.INVOICE, SalesDocumentStatus.DRAFT, false, List.of());
        when(documentRepo.findById(INVOICE_ID)).thenReturn(Optional.of(draftInvoice));

        var request = new DevolutionRequest(INVOICE_ID,
                List.of(item(PRODUCT_A_ID, BigDecimal.ONE)), "Razón");

        assertThatThrownBy(() -> useCase.processDevolution(request, USER_ID))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    var be = (BusinessException) ex;
                    assertThat(be.getErrorCode()).isEqualTo("DEV_NOT_ISSUED");
                    assertThat(be.getMessage()).contains("ISSUED");
                });
    }

    // ── Scenario 4: Invoice already has credit note → BusinessException ─────

    @Test
    void invoiceAlreadyHasCreditNote_throwsBusinessException() {
        var invoice = document(SalesDocumentType.INVOICE, SalesDocumentStatus.ISSUED, false,
                List.of(invoiceItem(PRODUCT_A_ID, QTY_5, PRICE_10000)));
        when(documentRepo.findById(INVOICE_ID)).thenReturn(Optional.of(invoice));
        when(documentRepo.findBySourceDocumentIdAndType(INVOICE_ID, SalesDocumentType.CREDIT_NOTE))
                .thenReturn(List.of(mock(SalesDocument.class)));

        var request = new DevolutionRequest(INVOICE_ID,
                List.of(item(PRODUCT_A_ID, BigDecimal.ONE)), "Razón");

        assertThatThrownBy(() -> useCase.processDevolution(request, USER_ID))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    var be = (BusinessException) ex;
                    assertThat(be.getErrorCode()).isEqualTo("DEV_DUPLICATE_CN");
                    assertThat(be.getMessage()).contains("nota crédito");
                });
    }

    // ── Scenario 5: Item not in invoice → BusinessException ─────────────────

    @Test
    void itemNotInInvoice_throwsBusinessException() {
        var invoice = document(SalesDocumentType.INVOICE, SalesDocumentStatus.ISSUED, false,
                List.of(invoiceItem(PRODUCT_A_ID, QTY_5, PRICE_10000),
                        invoiceItem(PRODUCT_B_ID, QTY_3, PRICE_10000)));
        when(documentRepo.findById(INVOICE_ID)).thenReturn(Optional.of(invoice));
        when(documentRepo.findBySourceDocumentIdAndType(INVOICE_ID, SalesDocumentType.CREDIT_NOTE))
                .thenReturn(List.of());

        var request = new DevolutionRequest(INVOICE_ID,
                List.of(item(PRODUCT_C_ID, BigDecimal.ONE)), "Razón");

        assertThatThrownBy(() -> useCase.processDevolution(request, USER_ID))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    var be = (BusinessException) ex;
                    assertThat(be.getErrorCode()).isEqualTo("DEV_ITEM_NOT_IN_INVOICE");
                    assertThat(be.getMessage()).contains("no está en la factura");
                });
    }

    // ── Scenario 6: Quantity exceeds original → BusinessException ───────────

    @Test
    void quantityExceedsOriginal_throwsBusinessException() {
        var invoice = document(SalesDocumentType.INVOICE, SalesDocumentStatus.ISSUED, false,
                List.of(invoiceItem(PRODUCT_A_ID, QTY_5, PRICE_10000)));
        when(documentRepo.findById(INVOICE_ID)).thenReturn(Optional.of(invoice));
        when(documentRepo.findBySourceDocumentIdAndType(INVOICE_ID, SalesDocumentType.CREDIT_NOTE))
                .thenReturn(List.of());

        var request = new DevolutionRequest(INVOICE_ID,
                List.of(item(PRODUCT_A_ID, QTY_7)), "Razón");

        assertThatThrownBy(() -> useCase.processDevolution(request, USER_ID))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    var be = (BusinessException) ex;
                    assertThat(be.getErrorCode()).isEqualTo("DEV_EXCESS_QTY");
                    assertThat(be.getMessage()).contains("excede la facturada");
                });
    }

    // ── Scenario 7: Full devolution restores stock ──────────────────────────

    @Test
    void fullDevolution_restoresStockAndCreatesCreditNote() {
        // Invoice with item A: qty=5, unitPrice=10,000
        var invoice = document(SalesDocumentType.INVOICE, SalesDocumentStatus.ISSUED, false,
                List.of(invoiceItem(PRODUCT_A_ID, QTY_5, PRICE_10000)));
        when(documentRepo.findById(INVOICE_ID)).thenReturn(Optional.of(invoice));
        when(documentRepo.findBySourceDocumentIdAndType(INVOICE_ID, SalesDocumentType.CREDIT_NOTE))
                .thenReturn(List.of());

        // Mock stock
        var stock = new InventoryStock(
                STOCK_ID, PRODUCT_A_ID, BATCH_ID, WAREHOUSE_ID,
                new BigDecimal("50"), BigDecimal.ZERO, BigDecimal.ZERO,
                OffsetDateTime.now(), OffsetDateTime.now());
        when(stockRepo.findByProductBatchWarehouse(PRODUCT_A_ID, BATCH_ID, WAREHOUSE_ID))
                .thenReturn(Optional.of(stock));

        // Mock document save: return with generated id
        var savedId = UUID.randomUUID();
        doAnswer(inv -> {
            var doc = inv.getArgument(0, SalesDocument.class);
            return new SalesDocument(
                    savedId, doc.type(), doc.status(), doc.documentNumber(),
                    doc.clientId(), doc.warehouseId(), doc.shiftId(), doc.cashRegisterId(),
                    doc.sourceDocumentId(), doc.totalNet(), doc.totalTax0(), doc.totalTax5(),
                    doc.totalTax8(), doc.totalTax19(), doc.totalAmount(),
                    doc.createdBy(), doc.createdAt(), doc.updatedAt(),
                    doc.items(), doc.dueDate(), doc.isCreditSale(), doc.reason());
        }).when(documentRepo).save(any(SalesDocument.class));

        // Stock save: return same
        when(stockRepo.save(any(InventoryStock.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        // Movement record: return null (not used)
        when(recordMovement.record(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(null);

        // Item save
        when(itemRepo.save(any(SaleItem.class))).thenAnswer(inv -> inv.getArgument(0));

        var request = new DevolutionRequest(INVOICE_ID,
                List.of(item(PRODUCT_A_ID, QTY_5)), "Devolución total");

        DevolutionResponse response = useCase.processDevolution(request, USER_ID);

        // Verify response
        assertThat(response.reversedItems()).isEqualTo(1);
        assertThat(response.creditNoteId()).isNotNull();

        // Verify credit note saved with negative amounts
        var docCaptor = ArgumentCaptor.forClass(SalesDocument.class);
        verify(documentRepo).save(docCaptor.capture());
        var creditNote = docCaptor.getValue();
        assertThat(creditNote.type()).isEqualTo(SalesDocumentType.CREDIT_NOTE);
        assertThat(creditNote.status()).isEqualTo(SalesDocumentStatus.ISSUED);
        assertThat(creditNote.sourceDocumentId()).isEqualTo(INVOICE_ID);
        // Subtotal for qty=5 at 10,000 = 50,000, with tax 19% → tax=9,500, total=59,500
        assertThat(creditNote.totalNet()).isEqualByComparingTo("-50000.00");
        assertThat(creditNote.totalAmount()).isEqualByComparingTo("-59500.00");
        assertThat(creditNote.reason()).isEqualTo("Devolución total");

        // Verify item has negative quantity
        var itemCaptor = ArgumentCaptor.forClass(SaleItem.class);
        verify(itemRepo).save(itemCaptor.capture());
        assertThat(itemCaptor.getValue().quantity()).isEqualByComparingTo("-5");
        assertThat(itemCaptor.getValue().unitPrice()).isEqualByComparingTo("10000.00");

        // Verify stock restored: currentQuantity went from 50 → 55
        var stockCaptor = ArgumentCaptor.forClass(InventoryStock.class);
        verify(stockRepo).save(stockCaptor.capture());
        assertThat(stockCaptor.getValue().currentQuantity()).isEqualByComparingTo("55");

        // Verify movement recorded with ENTRY type (REQ-POS-086) and correct quantities
        verify(recordMovement).record(
                eq(PRODUCT_A_ID), eq(BATCH_ID), eq(WAREHOUSE_ID),
                eq(MovementType.ENTRY),
                eq(QTY_5), eq(BigDecimal.ZERO), // unitCost is ZERO
                eq(new BigDecimal("50")), eq(new BigDecimal("55")),
                eq("SALE"), eq(savedId),
                any()
        );

        // Verify product repo recalculates total stock
        verify(productRepo).recalculateTotalStock(PRODUCT_A_ID);

        // Verify event published
        verify(eventPublisher).publishEvent(any(InvoiceIssuedEvent.class));
    }

    // ── Scenario 8: Partial devolution, cash sale (no AR adjustment) ──────

    @Test
    void partialDevolution_cashSale_doesNotAdjustAR() {
        var invoice = document(SalesDocumentType.INVOICE, SalesDocumentStatus.ISSUED, false,
                List.of(invoiceItem(PRODUCT_A_ID, QTY_5, PRICE_10000),
                        invoiceItem(PRODUCT_D_ID, QTY_3, PRICE_10000)));
        when(documentRepo.findById(INVOICE_ID)).thenReturn(Optional.of(invoice));
        when(documentRepo.findBySourceDocumentIdAndType(INVOICE_ID, SalesDocumentType.CREDIT_NOTE))
                .thenReturn(List.of());

        // Stock for product D (partial devolution)
        var stock = new InventoryStock(
                STOCK_ID, PRODUCT_D_ID, BATCH_ID, WAREHOUSE_ID,
                new BigDecimal("50"), BigDecimal.ZERO, BigDecimal.ZERO,
                OffsetDateTime.now(), OffsetDateTime.now());
        when(stockRepo.findByProductBatchWarehouse(PRODUCT_D_ID, BATCH_ID, WAREHOUSE_ID))
                .thenReturn(Optional.of(stock));

        var savedId = UUID.randomUUID();
        doAnswer(inv -> {
            var doc = inv.getArgument(0, SalesDocument.class);
            return new SalesDocument(
                    savedId, doc.type(), doc.status(), doc.documentNumber(),
                    doc.clientId(), doc.warehouseId(), doc.shiftId(), doc.cashRegisterId(),
                    doc.sourceDocumentId(), doc.totalNet(), doc.totalTax0(), doc.totalTax5(),
                    doc.totalTax8(), doc.totalTax19(), doc.totalAmount(),
                    doc.createdBy(), doc.createdAt(), doc.updatedAt(),
                    doc.items(), doc.dueDate(), doc.isCreditSale(), doc.reason());
        }).when(documentRepo).save(any(SalesDocument.class));

        when(stockRepo.save(any(InventoryStock.class))).thenAnswer(inv -> inv.getArgument(0));
        when(recordMovement.record(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(null);
        when(itemRepo.save(any(SaleItem.class))).thenAnswer(inv -> inv.getArgument(0));

        // Only devolve product D (qty=3), not product A
        var request = new DevolutionRequest(INVOICE_ID,
                List.of(item(PRODUCT_D_ID, QTY_3)), "Devolución parcial");

        DevolutionResponse response = useCase.processDevolution(request, USER_ID);

        assertThat(response.reversedItems()).isEqualTo(1);
        assertThat(response.arAdjustment()).isEqualByComparingTo("0.00");

        // Verify NO AR/ThirdParty interactions (cash sale)
        verify(arRepo, never()).findByDocumentId(any());
        verify(arRepo, never()).save(any(AccountsReceivable.class));
        verify(thirdPartyRepo, never()).findById(any());
        verify(thirdPartyRepo, never()).save(any(ThirdParty.class));
    }

    // ── Scenario 9: Credit sale devolution reduces AR ───────────────────────

    @Test
    void creditSaleDevolution_reducesARAndThirdPartyBalance() {
        var invoice = document(SalesDocumentType.INVOICE, SalesDocumentStatus.ISSUED, true,
                List.of(invoiceItem(PRODUCT_A_ID, QTY_5, PRICE_10000)));
        when(documentRepo.findById(INVOICE_ID)).thenReturn(Optional.of(invoice));
        when(documentRepo.findBySourceDocumentIdAndType(INVOICE_ID, SalesDocumentType.CREDIT_NOTE))
                .thenReturn(List.of());

        // Stock
        var stock = new InventoryStock(
                STOCK_ID, PRODUCT_A_ID, BATCH_ID, WAREHOUSE_ID,
                new BigDecimal("50"), BigDecimal.ZERO, BigDecimal.ZERO,
                OffsetDateTime.now(), OffsetDateTime.now());
        when(stockRepo.findByProductBatchWarehouse(PRODUCT_A_ID, BATCH_ID, WAREHOUSE_ID))
                .thenReturn(Optional.of(stock));

        var savedId = UUID.randomUUID();
        doAnswer(inv -> {
            var doc = inv.getArgument(0, SalesDocument.class);
            return new SalesDocument(
                    savedId, doc.type(), doc.status(), doc.documentNumber(),
                    doc.clientId(), doc.warehouseId(), doc.shiftId(), doc.cashRegisterId(),
                    doc.sourceDocumentId(), doc.totalNet(), doc.totalTax0(), doc.totalTax5(),
                    doc.totalTax8(), doc.totalTax19(), doc.totalAmount(),
                    doc.createdBy(), doc.createdAt(), doc.updatedAt(),
                    doc.items(), doc.dueDate(), doc.isCreditSale(), doc.reason());
        }).when(documentRepo).save(any(SalesDocument.class));

        when(stockRepo.save(any(InventoryStock.class))).thenAnswer(inv -> inv.getArgument(0));
        when(recordMovement.record(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(null);
        when(itemRepo.save(any(SaleItem.class))).thenAnswer(inv -> inv.getArgument(0));

        // AR with outstanding = 59,500 (totalAmount=59500, paidAmount=0)
        var ar = new AccountsReceivable(
                UUID.randomUUID(), CLIENT_ID, INVOICE_ID,
                new BigDecimal("59500.00"), BigDecimal.ZERO, new BigDecimal("59500.00"),
                null, AccountsReceivable.ArStatus.OPEN,
                null, null, null, BigDecimal.ZERO, null);
        when(arRepo.findByDocumentId(INVOICE_ID)).thenReturn(Optional.of(ar));

        // AR save
        when(arRepo.save(any(AccountsReceivable.class))).thenAnswer(inv -> inv.getArgument(0));

        // ThirdParty with currentBalance = 59,500
        var tp = thirdPartyWithBalance(new BigDecimal("59500.00"));
        when(thirdPartyRepo.findById(CLIENT_ID)).thenReturn(Optional.of(tp));
        when(thirdPartyRepo.save(any(ThirdParty.class))).thenAnswer(inv -> inv.getArgument(0));

        // Full devolution of item A (qty=5, unitPrice=10,000)
        var request = new DevolutionRequest(INVOICE_ID,
                List.of(item(PRODUCT_A_ID, QTY_5)), "Devolución crédito");

        DevolutionResponse response = useCase.processDevolution(request, USER_ID);

        // Response confirms AR adjustment
        assertThat(response.arAdjustment()).isEqualByComparingTo("50000.00"); // totalReturned = 50,000

        // Verify AR outstanding reduced: 59500 - 50000 = 9500
        var arCaptor = ArgumentCaptor.forClass(AccountsReceivable.class);
        verify(arRepo).save(arCaptor.capture());
        assertThat(arCaptor.getValue().outstanding()).isEqualByComparingTo("9500.00");

        // Verify ThirdParty.currentBalance reduced: 59500 - 50000 = 9500
        var tpCaptor = ArgumentCaptor.forClass(ThirdParty.class);
        verify(thirdPartyRepo).save(tpCaptor.capture());
        assertThat(tpCaptor.getValue().currentBalance()).isEqualByComparingTo("9500.00");
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private DevolutionRequest.DevolutionItem item(UUID productId, BigDecimal quantity) {
        return new DevolutionRequest.DevolutionItem(productId, quantity);
    }

    private SalesDocument document(SalesDocumentType type, SalesDocumentStatus status,
                                    boolean isCreditSale, List<SaleItem> items) {
        return new SalesDocument(
                INVOICE_ID, type, status, "DOC-001",
                CLIENT_ID, WAREHOUSE_ID, null, null, null,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO,
                USER_ID, null, null,
                items, null, isCreditSale, null);
    }

    private SaleItem invoiceItem(UUID productId, BigDecimal quantity, BigDecimal unitPrice) {
        // Full tax calculation: subtotal = qty * unitPrice, tax = subtotal * rate / 100
        var subtotal = quantity.multiply(unitPrice);
        var taxAmount = subtotal.multiply(TAX_RATE_19)
                .divide(new BigDecimal("100"), 2, java.math.RoundingMode.HALF_UP);
        return new SaleItem(
                UUID.randomUUID(), INVOICE_ID, productId,
                quantity, unitPrice,
                "IVA_19", TAX_RATE_19, taxAmount, subtotal,
                1, BATCH_ID);
    }

    private ThirdParty thirdPartyWithBalance(BigDecimal balance) {
        return new ThirdParty(
                CLIENT_ID, "123", "Cliente Test", ThirdParty.ThirdPartyType.CLIENT,
                null, BigDecimal.ZERO, balance,
                ThirdParty.PersonType.NATURAL, ThirdParty.TaxRegime.ORDINARIO,
                List.of(), "001", null, true, null, null,
                null, null, null, null, null, null, null, null, null, null,
                null, null, 0, null, null, null, null, null, null, null,
                null, false, false, false, false, false, null);
    }
}
