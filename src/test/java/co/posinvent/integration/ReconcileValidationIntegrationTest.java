package co.posinvent.integration;

import co.posinvent.application.dto.GoodsReceiptRequest;
import co.posinvent.application.dto.SupplierInvoiceRequest;
import co.posinvent.application.dto.SupplierInvoiceResponse;
import co.posinvent.application.usecase.CreateGoodsReceiptUseCase;
import co.posinvent.application.usecase.SupplierInvoiceUseCase;
import co.posinvent.domain.exception.BusinessException;
import co.posinvent.domain.exception.ResourceNotFoundException;
import co.posinvent.domain.model.InvoiceStatus;
import co.posinvent.domain.model.PurchaseLineItem;
import co.posinvent.domain.model.PurchaseOrder;
import co.posinvent.domain.model.PurchaseOrderStatus;
import co.posinvent.domain.model.ThirdParty;
import co.posinvent.domain.model.Warehouse;
import co.posinvent.domain.repository.AccountsPayableRepository;
import co.posinvent.domain.repository.PurchaseOrderRepository;
import co.posinvent.infrastructure.adapters.out.persistence.TestDataFactory;
import co.posinvent.infrastructure.adapters.out.persistence.ThirdPartyEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration guard for Auditoría Compras, hallazgo A9 (WU-44).
 *
 * <p>{@code SupplierInvoiceUseCase.reconcile} must no longer reconcile orphan invoices or
 * over-invoiced receipts: it validates that the invoice references an existing, non-cancelled
 * purchase order ({@code INV_NO_PURCHASE_ORDER}/{@code INV_OC_NOT_RECEIVABLE}), that each OC has at
 * least one goods receipt ({@code INV_NO_RECEIPT}) and that the invoice subtotal does not exceed the
 * value actually received ({@code Σ receivedQty × actualCost}) beyond a 1% tolerance
 * ({@code INV_AMOUNT_MISMATCH}).</p>
 *
 * <p>Deliberately NOT {@code @Transactional}: fixtures and the use cases commit their own
 * transactions, mirroring production.</p>
 */
class ReconcileValidationIntegrationTest extends AbstractIntegrationTest {

    @Autowired private SupplierInvoiceUseCase supplierInvoiceUseCase;
    @Autowired private CreateGoodsReceiptUseCase createGoodsReceiptUseCase;
    @Autowired private PurchaseOrderRepository purchaseOrderRepository;
    @Autowired private AccountsPayableRepository accountsPayableRepository;

    private UUID supplierId;
    private UUID warehouseId;
    private UUID productId;

    @BeforeEach
    void setUp() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            warehouseId = TestDataFactory.createWarehouse(
                    em, "Reconcile WH", Warehouse.WarehouseType.CORTES);
            productId = TestDataFactory.createProduct(
                    em, "RECON", "Producto conciliación", false, true);

            var supplier = new ThirdPartyEntity();
            supplier.setNumIdentification("SUP-RECON-" + UUID.randomUUID().toString().substring(0, 8));
            supplier.setName("Supplier Conciliación");
            supplier.setType(ThirdParty.ThirdPartyType.SUPPLIER);
            supplier.setPersonType(ThirdParty.PersonType.JURIDICA);
            supplier.setTaxRegime(ThirdParty.TaxRegime.ORDINARIO);
            supplier.setTaxResponsibilities(List.of());
            supplier.setActive(true);
            supplier.setCreditLimit(BigDecimal.ZERO);
            em.persist(supplier);
            supplierId = supplier.getId();
        });
    }

    @Test
    void invoiceWithoutPurchaseOrderIsRejected() {
        var created = createInvoice(new BigDecimal("1000.00"), List.of());

        assertThatThrownBy(() -> supplierInvoiceUseCase.reconcile(created.id()))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo("INV_NO_PURCHASE_ORDER");

        assertThat(invoiceStatus(created.id())).isEqualTo(InvoiceStatus.PENDING);
        assertThat(accountsPayableRepository.findByDocumentId(created.id())).isEmpty();
    }

    @Test
    void invoiceReferencingUnknownPurchaseOrderIsRejected() {
        // The invoice_orders FK requires a real OC at create time; re-point the link to a missing
        // OC to reach the not-found guard at reconcile.
        var realOcId = createPurchaseOrder(PurchaseOrderStatus.PENDING).id();
        var created = createInvoice(new BigDecimal("1000.00"), List.of(realOcId));

        repointInvoiceOrderLink(created.id(), UUID.randomUUID());
        try {
            assertThatThrownBy(() -> supplierInvoiceUseCase.reconcile(created.id()))
                    .isInstanceOf(ResourceNotFoundException.class);
        } finally {
            repointInvoiceOrderLink(created.id(), realOcId);
        }
    }

    @Test
    void purchaseOrderWithoutReceiptIsRejected() {
        var oc = createPurchaseOrder(PurchaseOrderStatus.PENDING);
        var created = createInvoice(new BigDecimal("1000.00"), List.of(oc.id()));

        assertThatThrownBy(() -> supplierInvoiceUseCase.reconcile(created.id()))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo("INV_NO_RECEIPT");

        assertThat(invoiceStatus(created.id())).isEqualTo(InvoiceStatus.PENDING);
    }

    @Test
    void cancelledPurchaseOrderIsNotReceivable() {
        var oc = createPurchaseOrder(PurchaseOrderStatus.CANCELLED);
        var created = createInvoice(new BigDecimal("1000.00"), List.of(oc.id()));

        assertThatThrownBy(() -> supplierInvoiceUseCase.reconcile(created.id()))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo("INV_OC_NOT_RECEIVABLE");
    }

    @Test
    void invoiceExceedingReceivedValueIsRejected() {
        var ocId = receivedPurchaseOrder(new BigDecimal("1000.00"));
        var created = createInvoice(new BigDecimal("2000.00"), List.of(ocId));

        assertThatThrownBy(() -> supplierInvoiceUseCase.reconcile(created.id()))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo("INV_AMOUNT_MISMATCH");

        assertThat(invoiceStatus(created.id())).isEqualTo(InvoiceStatus.PENDING);
    }

    @Test
    void invoiceWithinToleranceOfReceivedValueIsReconciled() {
        // Received 1000.00; 1% tolerance = 10.00, so a 1005.00 subtotal is acceptable.
        var ocId = receivedPurchaseOrder(new BigDecimal("1000.00"));
        var created = createInvoice(new BigDecimal("1005.00"), List.of(ocId));

        var reconciled = supplierInvoiceUseCase.reconcile(created.id());

        assertThat(reconciled.status()).isEqualTo(InvoiceStatus.RECONCILED);
    }

    @Test
    void matchingInvoiceIsReconciledAndCreatesAccountsPayable() {
        var ocId = receivedPurchaseOrder(new BigDecimal("1000.00"));
        var created = createInvoice(new BigDecimal("1000.00"), List.of(ocId));

        var reconciled = supplierInvoiceUseCase.reconcile(created.id());

        assertThat(reconciled.status()).isEqualTo(InvoiceStatus.RECONCILED);
        var ap = accountsPayableRepository.findByDocumentId(created.id()).orElseThrow();
        assertThat(ap.outstanding()).isEqualByComparingTo("1000.00");
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private PurchaseOrder createPurchaseOrder(PurchaseOrderStatus status) {
        var holder = new PurchaseOrder[1];
        new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
            var line = new PurchaseLineItem(
                    null, null, productId, warehouseId,
                    new BigDecimal("1000.00"), BigDecimal.ZERO, BigDecimal.ONE,
                    BigDecimal.ZERO, "EXENTO", 1);
            holder[0] = purchaseOrderRepository.save(new PurchaseOrder(
                    null, supplierId, status, LocalDate.now(),
                    "OC-RECON-" + UUID.randomUUID().toString().substring(0, 6),
                    null, null, null, null, null, null, "COP",
                    TEST_USER_ID, null, null, null, List.of(line)));
        });
        return holder[0];
    }

    /**
     * Creates an OC fully received at the given value (receivedQty × actualCost = value) and records
     * the goods receipt through the real use case, so the OC reaches {@code RECEIVED}.
     */
    private UUID receivedPurchaseOrder(BigDecimal value) {
        var ocId = createPurchaseOrder(PurchaseOrderStatus.PENDING).id();
        createGoodsReceiptUseCase.process(new GoodsReceiptRequest(
                ocId,
                List.of(new GoodsReceiptRequest.ReceiptLine(
                        productId, warehouseId, value, BigDecimal.ONE, null))),
                TEST_USER_ID);
        return ocId;
    }

    private SupplierInvoiceResponse createInvoice(BigDecimal subtotal, List<UUID> ocIds) {
        return supplierInvoiceUseCase.create(new SupplierInvoiceRequest(
                supplierId,
                "FAC-RECON-" + UUID.randomUUID().toString().substring(0, 8),
                LocalDate.now(), LocalDate.now().plusDays(30),
                subtotal, BigDecimal.ZERO, BigDecimal.ZERO, subtotal,
                null, ocIds), TEST_USER_ID);
    }

    private InvoiceStatus invoiceStatus(UUID invoiceId) {
        return supplierInvoiceUseCase.getById(invoiceId).status();
    }

    /**
     * Re-points the invoice↔OC link directly, bypassing the {@code invoice_orders} FK trigger. This
     * reproduces the otherwise-unreachable "OC no longer exists" state; the caller restores the link.
     */
    private void repointInvoiceOrderLink(UUID invoiceId, UUID targetOcId) {
        new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
            em.createNativeQuery("ALTER TABLE invoice_orders DISABLE TRIGGER ALL").executeUpdate();
            try {
                em.createNativeQuery(
                                "UPDATE invoice_orders SET oc_id = :oc WHERE invoice_id = :inv")
                        .setParameter("oc", targetOcId)
                        .setParameter("inv", invoiceId)
                        .executeUpdate();
            } finally {
                em.createNativeQuery("ALTER TABLE invoice_orders ENABLE TRIGGER ALL").executeUpdate();
            }
        });
    }
}
