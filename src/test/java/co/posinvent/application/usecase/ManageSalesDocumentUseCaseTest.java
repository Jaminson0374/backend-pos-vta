package co.posinvent.application.usecase;

import co.posinvent.application.dto.SaleItemRequest;
import co.posinvent.domain.exception.BusinessException;
import co.posinvent.domain.model.*;
import co.posinvent.domain.repository.*;
import co.posinvent.domain.service.FefoPicker;
import co.posinvent.infrastructure.adapters.out.security.CurrentActorProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for stock reservation on ORDER confirmation/cancellation (audit A1).
 *
 * <p>The reservation must be keyed exclusively by the item's {@code batchId}: an
 * item without a batch is skipped instead of falling back to the product id,
 * which never matched an {@code inventory_stock} row and made the reservation a
 * silent no-op.</p>
 */
@ExtendWith(MockitoExtension.class)
class ManageSalesDocumentUseCaseTest {

    @Mock private SalesDocumentRepository documentRepo;
    @Mock private SaleItemRepository itemRepo;
    @Mock private ThirdPartyRepository thirdPartyRepo;
    @Mock private StockRepository stockRepo;
    @Mock private WarehouseRepository warehouseRepo;
    @Mock private ProductRepository productRepo;
    @Mock private AccountsReceivableUseCase accountsReceivableUseCase;
    @Mock private AccountsReceivableRepository accountsReceivableRepository;
    @Mock private FefoPicker fefoPicker;
    @Mock private RecordMovementUseCase recordMovement;
    @Mock private CompanyConfigRepository companyConfigRepo;
    @Mock private co.posinvent.infrastructure.adapters.out.security.CurrentActorProvider actorProvider;
    @Mock private SalesDocumentAccessGuard accessGuard;

    private ManageSalesDocumentUseCase useCase;

    private static final UUID DOC_ID = UUID.randomUUID();
    private static final UUID PRODUCT_ID = UUID.randomUUID();
    private static final UUID BATCH_ID = UUID.randomUUID();
    private static final UUID WAREHOUSE_ID = UUID.randomUUID();
    private static final UUID CLIENT_ID = UUID.randomUUID();
    private static final UUID STOCK_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        useCase = new ManageSalesDocumentUseCase(
                documentRepo, itemRepo, thirdPartyRepo, stockRepo, warehouseRepo,
                productRepo, accountsReceivableRepository, accountsReceivableUseCase,
                fefoPicker, recordMovement,
                new DiscountEngineService(productRepo, companyConfigRepo),
                actorProvider, accessGuard);

        lenient().when(actorProvider.current()).thenReturn(
                new CurrentActorProvider.CurrentActor(USER_ID, Set.of("CAJERO")));
    }

    @Test
    void deniedOperatePropagatesAndWritesNoStockOrDocument() {
        var item = item(new BigDecimal("5"), BATCH_ID);
        when(documentRepo.findById(DOC_ID))
                .thenReturn(Optional.of(order(SalesDocumentStatus.DRAFT, List.of(item))));
        doThrow(new AccessDeniedException("No tiene permisos para operar este documento."))
                .when(accessGuard).requireOperate(any(), any(), eq("TRANSITION_SALES_DOCUMENT"));

        assertThatThrownBy(() -> useCase.transitionDocument(DOC_ID, SalesDocumentStatus.CONFIRMED))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("No tiene permisos para operar");

        verify(accessGuard).requireOperate(any(), any(), eq("TRANSITION_SALES_DOCUMENT"));
        verifyNoInteractions(stockRepo);
        verify(documentRepo, never()).save(any(SalesDocument.class));
    }

    @Test
    void confirmingOrderWithBatchIdIncrementsCommittedQuantity() {
        var item = item(new BigDecimal("5"), BATCH_ID);
        when(documentRepo.findById(DOC_ID)).thenReturn(Optional.of(order(SalesDocumentStatus.DRAFT, List.of(item))));
        when(documentRepo.save(any(SalesDocument.class))).thenAnswer(inv -> inv.getArgument(0));
        when(stockRepo.findByProductBatchWarehouse(PRODUCT_ID, BATCH_ID, WAREHOUSE_ID))
                .thenReturn(Optional.of(stock(new BigDecimal("10"), new BigDecimal("2"))));

        useCase.transitionDocument(DOC_ID, SalesDocumentStatus.CONFIRMED);

        var captor = ArgumentCaptor.forClass(InventoryStock.class);
        verify(stockRepo).save(captor.capture());
        assertThat(captor.getValue().committedQuantity()).isEqualByComparingTo("7");
    }

    @Test
    void confirmingOrderWithInsufficientAvailableStockFails() {
        var item = item(new BigDecimal("5"), BATCH_ID);
        when(documentRepo.findById(DOC_ID)).thenReturn(Optional.of(order(SalesDocumentStatus.DRAFT, List.of(item))));
        when(stockRepo.findByProductBatchWarehouse(PRODUCT_ID, BATCH_ID, WAREHOUSE_ID))
                .thenReturn(Optional.of(stock(new BigDecimal("10"), new BigDecimal("8"))));

        assertThatThrownBy(() -> useCase.transitionDocument(DOC_ID, SalesDocumentStatus.CONFIRMED))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", "INSUFFICIENT_AVAILABLE_STOCK");
        verify(stockRepo, never()).save(any(InventoryStock.class));
    }

    @Test
    void cancellingConfirmedOrderReleasesCommittedQuantity() {
        var item = item(new BigDecimal("5"), BATCH_ID);
        when(documentRepo.findById(DOC_ID)).thenReturn(Optional.of(order(SalesDocumentStatus.CONFIRMED, List.of(item))));
        when(documentRepo.save(any(SalesDocument.class))).thenAnswer(inv -> inv.getArgument(0));
        when(stockRepo.findByProductBatchWarehouse(PRODUCT_ID, BATCH_ID, WAREHOUSE_ID))
                .thenReturn(Optional.of(stock(new BigDecimal("10"), new BigDecimal("10"))));

        useCase.transitionDocument(DOC_ID, SalesDocumentStatus.CANCELLED);

        var captor = ArgumentCaptor.forClass(InventoryStock.class);
        verify(stockRepo).save(captor.capture());
        assertThat(captor.getValue().committedQuantity()).isEqualByComparingTo("5");
    }

    @Test
    void cancellingClampsCommittedQuantityAtZero() {
        var item = item(new BigDecimal("5"), BATCH_ID);
        when(documentRepo.findById(DOC_ID)).thenReturn(Optional.of(order(SalesDocumentStatus.CONFIRMED, List.of(item))));
        when(documentRepo.save(any(SalesDocument.class))).thenAnswer(inv -> inv.getArgument(0));
        when(stockRepo.findByProductBatchWarehouse(PRODUCT_ID, BATCH_ID, WAREHOUSE_ID))
                .thenReturn(Optional.of(stock(new BigDecimal("10"), new BigDecimal("3"))));

        useCase.transitionDocument(DOC_ID, SalesDocumentStatus.CANCELLED);

        var captor = ArgumentCaptor.forClass(InventoryStock.class);
        verify(stockRepo).save(captor.capture());
        assertThat(captor.getValue().committedQuantity()).isEqualByComparingTo("0");
    }

    @Test
    void nullBatchItemIsSkippedWithoutTouchingStock() {
        var item = item(new BigDecimal("5"), null);
        when(documentRepo.findById(DOC_ID)).thenReturn(Optional.of(order(SalesDocumentStatus.DRAFT, List.of(item))));
        when(documentRepo.save(any(SalesDocument.class))).thenAnswer(inv -> inv.getArgument(0));

        useCase.transitionDocument(DOC_ID, SalesDocumentStatus.CONFIRMED);

        verify(stockRepo, never()).findByProductBatchWarehouse(any(), any(), any());
        verify(stockRepo, never()).save(any(InventoryStock.class));
    }

    @Test
    void addItemPropagatesBatchIdToSaleItem() {
        when(documentRepo.findById(DOC_ID)).thenReturn(Optional.of(order(SalesDocumentStatus.DRAFT, List.of())));
        when(documentRepo.save(any(SalesDocument.class))).thenAnswer(inv -> inv.getArgument(0));

        useCase.addItem(DOC_ID, new SaleItemRequest(PRODUCT_ID, new BigDecimal("2"),
                new BigDecimal("50"), "EXENTO", BATCH_ID));

        var captor = ArgumentCaptor.forClass(SaleItem.class);
        verify(itemRepo).save(captor.capture());
        assertThat(captor.getValue().batchId()).isEqualTo(BATCH_ID);
    }

    @Test
    void creditInvoiceWithinDerivedLimitIsIssued() {
        var invoice = creditInvoice(DOC_ID, new BigDecimal("300"));
        when(documentRepo.findById(DOC_ID)).thenReturn(Optional.of(invoice));
        when(documentRepo.save(any(SalesDocument.class))).thenAnswer(inv -> inv.getArgument(0));
        when(thirdPartyRepo.findById(CLIENT_ID))
                .thenReturn(Optional.of(client(new BigDecimal("500"))));
        when(accountsReceivableRepository.sumOutstandingByClientId(CLIENT_ID))
                .thenReturn(new BigDecimal("100"));

        useCase.transitionDocument(DOC_ID, SalesDocumentStatus.ISSUED);

        var captor = ArgumentCaptor.forClass(SalesDocument.class);
        verify(documentRepo).save(captor.capture());
        assertThat(captor.getValue().status()).isEqualTo(SalesDocumentStatus.ISSUED);
        verify(accountsReceivableUseCase, times(1)).createFromInvoice(any(SalesDocument.class));
    }

    @Test
    void secondCreditInvoiceExceedingDerivedOutstandingIsRejected() {
        var first = creditInvoice(DOC_ID, new BigDecimal("400"));
        var second = creditInvoice(DOC_ID, new BigDecimal("400"));
        when(documentRepo.findById(DOC_ID)).thenReturn(Optional.of(first), Optional.of(second));
        when(documentRepo.save(any(SalesDocument.class))).thenAnswer(inv -> inv.getArgument(0));
        when(thirdPartyRepo.findById(CLIENT_ID))
                .thenReturn(Optional.of(client(new BigDecimal("500"))));
        when(accountsReceivableRepository.sumOutstandingByClientId(CLIENT_ID))
                .thenReturn(BigDecimal.ZERO, new BigDecimal("400"));

        useCase.transitionDocument(DOC_ID, SalesDocumentStatus.ISSUED);

        assertThatThrownBy(() -> useCase.transitionDocument(DOC_ID, SalesDocumentStatus.ISSUED))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", "CREDIT_LIMIT_EXCEEDED");
        verify(accountsReceivableUseCase, times(1)).createFromInvoice(any(SalesDocument.class));
    }

    @Test
    void creditValidationIgnoresLegacyStaticBalance() {
        var invoice = creditInvoice(DOC_ID, new BigDecimal("200"));
        when(documentRepo.findById(DOC_ID)).thenReturn(Optional.of(invoice));
        when(thirdPartyRepo.findById(CLIENT_ID))
                .thenReturn(Optional.of(client(new BigDecimal("500"))));
        when(accountsReceivableRepository.sumOutstandingByClientId(CLIENT_ID))
                .thenReturn(new BigDecimal("400"));

        assertThatThrownBy(() -> useCase.transitionDocument(DOC_ID, SalesDocumentStatus.ISSUED))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", "CREDIT_LIMIT_EXCEEDED");
        verify(documentRepo, never()).save(any(SalesDocument.class));
    }

    private SaleItem item(BigDecimal quantity, UUID batchId) {
        return new SaleItem(null, DOC_ID, PRODUCT_ID, quantity, new BigDecimal("100"),
                "EXENTO", BigDecimal.ZERO, BigDecimal.ZERO, quantity.multiply(new BigDecimal("100")),
                1, batchId);
    }

    private SalesDocument order(SalesDocumentStatus status, List<SaleItem> items) {
        return new SalesDocument(DOC_ID, SalesDocumentType.ORDER, status, "ORD-001",
                CLIENT_ID, WAREHOUSE_ID, null, null, null,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, USER_ID, OffsetDateTime.now(), OffsetDateTime.now(),
                items, null, false, null);
    }

    private SalesDocument creditInvoice(UUID id, BigDecimal total) {
        return new SalesDocument(id, SalesDocumentType.INVOICE, SalesDocumentStatus.DRAFT, "INV-001",
                CLIENT_ID, WAREHOUSE_ID, null, null, null,
                total, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                total, USER_ID, OffsetDateTime.now(), OffsetDateTime.now(),
                List.of(), null, true, null);
    }

    private ThirdParty client(BigDecimal creditLimit) {
        return new ThirdParty(
                CLIENT_ID, "900123456", "Cliente Test", ThirdParty.ThirdPartyType.CLIENT,
                null, creditLimit,
                ThirdParty.PersonType.JURIDICA, ThirdParty.TaxRegime.ORDINARIO,
                List.of(), "001", null, true, null, null,
                null, null, null, null, null, null, null, null, null, null,
                null, null, 0, null, null, null, null, null, null, null,
                null, false, false, false, false, false, null);
    }

    private InventoryStock stock(BigDecimal current, BigDecimal committed) {
        return new InventoryStock(STOCK_ID, PRODUCT_ID, BATCH_ID, WAREHOUSE_ID,
                current, committed, BigDecimal.ZERO, OffsetDateTime.now(), null);
    }
}
