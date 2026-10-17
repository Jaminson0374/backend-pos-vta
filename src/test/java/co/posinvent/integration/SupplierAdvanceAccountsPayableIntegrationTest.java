package co.posinvent.integration;

import co.posinvent.application.dto.AdvanceRequest;
import co.posinvent.application.dto.ApplyAdvanceRequest;
import co.posinvent.application.dto.GoodsReceiptRequest;
import co.posinvent.application.dto.SupplierInvoiceRequest;
import co.posinvent.application.usecase.AccountsPayableUseCase;
import co.posinvent.application.usecase.ApplyAdvanceUseCase;
import co.posinvent.application.usecase.CreateGoodsReceiptUseCase;
import co.posinvent.application.usecase.PaymentUseCase;
import co.posinvent.application.usecase.SupplierInvoiceUseCase;
import co.posinvent.domain.model.AccountsPayable;
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

/**
 * Integration guard for Auditoría Compras hallazgo M3 (WU-45a): applying a supplier advance to an
 * invoice must create/reconcile the AccountsPayable instead of only flipping the invoice status.
 *
 * <p>Before this WU the advance moved a PENDING invoice to RECONCILED without creating a CxP, and
 * the invoice was never marked PAID even when the advance fully covered it.</p>
 *
 * <p>Deliberately NOT {@code @Transactional}: fixtures and the use cases commit their own
 * transactions so the AFTER_COMMIT accounting listeners run like in production.</p>
 */
class SupplierAdvanceAccountsPayableIntegrationTest extends AbstractIntegrationTest {

    @Autowired private PaymentUseCase paymentUseCase;
    @Autowired private ApplyAdvanceUseCase applyAdvanceUseCase;
    @Autowired private SupplierInvoiceUseCase supplierInvoiceUseCase;
    @Autowired private AccountsPayableUseCase accountsPayableUseCase;
    @Autowired private AccountsPayableRepository accountsPayableRepository;
    @Autowired private CreateGoodsReceiptUseCase createGoodsReceiptUseCase;
    @Autowired private PurchaseOrderRepository purchaseOrderRepository;

    private UUID supplierId;
    private UUID warehouseId;
    private UUID productId;

    @BeforeEach
    void setUp() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            var supplier = new ThirdPartyEntity();
            supplier.setNumIdentification("SUP-ADV-CXP-" + UUID.randomUUID().toString().substring(0, 8));
            supplier.setName("Supplier Advance CxP");
            supplier.setType(ThirdParty.ThirdPartyType.SUPPLIER);
            supplier.setPersonType(ThirdParty.PersonType.JURIDICA);
            supplier.setTaxRegime(ThirdParty.TaxRegime.ORDINARIO);
            supplier.setTaxResponsibilities(List.of());
            supplier.setActive(true);
            supplier.setCreditLimit(BigDecimal.ZERO);
            supplier.setCurrentBalance(BigDecimal.ZERO);
            em.persist(supplier);
            supplierId = supplier.getId();

            warehouseId = TestDataFactory.createWarehouse(
                    em, "CxP Advance WH", Warehouse.WarehouseType.CORTES);
            productId = TestDataFactory.createProduct(
                    em, "CXPA", "Producto anticipo CxP", false, true);
        });
    }

    @Test
    void applyingPartOfAnAdvanceCreatesTheCxPAndLeavesItPartial() {
        var invoiceId = pendingInvoice(new BigDecimal("1000.00"));
        assertThat(accountsPayableRepository.findByDocumentId(invoiceId)).isEmpty();

        var advanceId = advance("600.00");
        applyAdvanceUseCase.apply(
                new ApplyAdvanceRequest(advanceId, invoiceId, new BigDecimal("600.00")), TEST_USER_ID);

        var ap = ap(invoiceId);
        assertThat(ap.totalAmount()).isEqualByComparingTo("1000.00");
        assertThat(ap.paidAmount()).isEqualByComparingTo("600.00");
        assertThat(ap.outstanding()).isEqualByComparingTo("400.00");
        assertThat(ap.status()).isEqualTo(AccountsPayable.ApStatus.PARTIAL);
        assertThat(invoiceStatus(invoiceId)).isEqualTo(InvoiceStatus.RECONCILED);
        assertThat(accountsPayableUseCase.supplierOutstanding(supplierId)).isEqualByComparingTo("400.00");
    }

    @Test
    void applyingAFullAdvanceMarksBothTheCxPAndTheInvoicePaid() {
        var invoiceId = pendingInvoice(new BigDecimal("1000.00"));

        var advanceId = advance("1000.00");
        applyAdvanceUseCase.apply(
                new ApplyAdvanceRequest(advanceId, invoiceId, new BigDecimal("1000.00")), TEST_USER_ID);

        var ap = ap(invoiceId);
        assertThat(ap.paidAmount()).isEqualByComparingTo("1000.00");
        assertThat(ap.outstanding()).isEqualByComparingTo("0.00");
        assertThat(ap.status()).isEqualTo(AccountsPayable.ApStatus.PAID);
        assertThat(invoiceStatus(invoiceId)).isEqualTo(InvoiceStatus.PAID);
        assertThat(accountsPayableUseCase.supplierOutstanding(supplierId)).isEqualByComparingTo("0.00");
    }

    @Test
    void applyingAnAdvanceToAReconciledInvoiceReducesTheExistingCxP() {
        var invoiceId = reconciledInvoice(new BigDecimal("1000.00"));
        assertThat(ap(invoiceId).outstanding()).isEqualByComparingTo("1000.00");

        var advanceId = advance("400.00");
        applyAdvanceUseCase.apply(
                new ApplyAdvanceRequest(advanceId, invoiceId, new BigDecimal("400.00")), TEST_USER_ID);

        var ap = ap(invoiceId);
        assertThat(ap.paidAmount()).isEqualByComparingTo("400.00");
        assertThat(ap.outstanding()).isEqualByComparingTo("600.00");
        assertThat(ap.status()).isEqualTo(AccountsPayable.ApStatus.PARTIAL);
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private UUID advance(String amount) {
        return paymentUseCase.createAdvance(new AdvanceRequest(
                supplierId, new BigDecimal(amount), LocalDate.now(), "TRANSFERENCIA",
                "ANT-CXP-" + UUID.randomUUID().toString().substring(0, 6), null),
                TEST_USER_ID).id();
    }

    /** A PENDING invoice backed by a fully received OC (reconcilable, but still without a CxP). */
    private UUID pendingInvoice(BigDecimal total) {
        var ocId = receivedPurchaseOrder(total);
        return supplierInvoiceUseCase.create(new SupplierInvoiceRequest(
                supplierId,
                "FAC-ADV-CXP-" + UUID.randomUUID().toString().substring(0, 8),
                LocalDate.now(), LocalDate.now().plusDays(30),
                total, BigDecimal.ZERO, BigDecimal.ZERO, total,
                null, List.of(ocId)), TEST_USER_ID).id();
    }

    private UUID reconciledInvoice(BigDecimal total) {
        var invoiceId = pendingInvoice(total);
        supplierInvoiceUseCase.reconcile(invoiceId);
        return invoiceId;
    }

    /** Creates a fully received OC whose received value ({@code receivedQty × actualCost}) equals {@code value}. */
    private UUID receivedPurchaseOrder(BigDecimal value) {
        var holder = new PurchaseOrder[1];
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            var line = new PurchaseLineItem(
                    null, null, productId, warehouseId,
                    value, BigDecimal.ZERO, BigDecimal.ONE,
                    BigDecimal.ZERO, "EXENTO", 1);
            holder[0] = purchaseOrderRepository.save(new PurchaseOrder(
                    null, supplierId, PurchaseOrderStatus.PENDING, LocalDate.now(),
                    "OC-ADV-CXP-" + UUID.randomUUID().toString().substring(0, 6),
                    null, null, null, null, null, null, "COP",
                    TEST_USER_ID, null, null, null, List.of(line)));
        });
        createGoodsReceiptUseCase.process(new GoodsReceiptRequest(
                holder[0].id(),
                List.of(new GoodsReceiptRequest.ReceiptLine(
                        productId, warehouseId, value, BigDecimal.ONE, null))),
                TEST_USER_ID);
        return holder[0].id();
    }

    private AccountsPayable ap(UUID invoiceId) {
        return accountsPayableRepository.findByDocumentId(invoiceId).orElseThrow();
    }

    private InvoiceStatus invoiceStatus(UUID invoiceId) {
        return supplierInvoiceUseCase.getById(invoiceId).status();
    }
}
