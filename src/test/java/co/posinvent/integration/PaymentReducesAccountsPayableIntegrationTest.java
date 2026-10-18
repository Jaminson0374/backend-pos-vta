package co.posinvent.integration;

import co.posinvent.application.dto.GoodsReceiptRequest;
import co.posinvent.application.dto.PaymentRequest;
import co.posinvent.application.dto.PaymentResponse;
import co.posinvent.application.dto.SupplierInvoiceRequest;
import co.posinvent.application.dto.SupplierInvoiceResponse;
import co.posinvent.application.usecase.AccountsPayableUseCase;
import co.posinvent.application.usecase.CreateGoodsReceiptUseCase;
import co.posinvent.application.usecase.PaymentUseCase;
import co.posinvent.application.usecase.SupplierInvoiceUseCase;
import co.posinvent.domain.exception.BusinessException;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration guard for Auditoría Compras hallazgo C2 (crítico) and A1.
 *
 * <p>C2: paying a supplier must update the AccountsPayable ({@code paidAmount}/{@code outstanding}/
 * {@code status}) for every invoice referenced by the payment. Before this WU the payment only
 * touched the shared static supplier balance, leaving the CxP stale.</p>
 *
 * <p>A1: the overpayment check compares the applied amount against the <em>remaining</em> balance
 * (the CxP outstanding / total minus previous payments), not the raw invoice total.</p>
 *
 * <p>Deliberately NOT {@code @Transactional}: fixtures and the use cases commit their own
 * transactions so the AFTER_COMMIT accounting listener runs exactly like in production.</p>
 */
class PaymentReducesAccountsPayableIntegrationTest extends AbstractIntegrationTest {

    private static final String SUPPLIER_NAME = "Supplier CxP Payment";

    @Autowired private SupplierInvoiceUseCase supplierInvoiceUseCase;
    @Autowired private PaymentUseCase paymentUseCase;
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
            supplierId = createSupplier();
            warehouseId = TestDataFactory.createWarehouse(
                    em, "CxP Payment WH", Warehouse.WarehouseType.CORTES);
            productId = TestDataFactory.createProduct(
                    em, "CXP", "Producto pago CxP", false, true);
        });
    }

    @Test
    void partialPaymentReducesOutstandingAndKeepsInvoiceReconciled() {
        var invoiceId = reconciledInvoice(new BigDecimal("1000.00"));

        var apBefore = accountsPayable(invoiceId);
        assertThat(apBefore.outstanding()).isEqualByComparingTo("1000.00");
        assertThat(apBefore.status()).isEqualTo(AccountsPayable.ApStatus.OPEN);

        pay(invoiceId, "400.00");

        var apAfter = accountsPayable(invoiceId);
        assertThat(apAfter.paidAmount()).isEqualByComparingTo("400.00");
        assertThat(apAfter.outstanding()).isEqualByComparingTo("600.00");
        assertThat(apAfter.status()).isEqualTo(AccountsPayable.ApStatus.PARTIAL);
        assertThat(invoiceStatus(invoiceId)).isEqualTo(InvoiceStatus.RECONCILED);

        // Derived supplier balance is SUM(outstanding) of the active CxP rows.
        assertThat(accountsPayableUseCase.supplierOutstanding(supplierId)).isEqualByComparingTo("600.00");
    }

    @Test
    void fullPaymentClearsOutstandingAndMarksInvoicePaid() {
        var invoiceId = reconciledInvoice(new BigDecimal("1000.00"));

        pay(invoiceId, "1000.00");

        var ap = accountsPayable(invoiceId);
        assertThat(ap.outstanding()).isEqualByComparingTo("0.00");
        assertThat(ap.paidAmount()).isEqualByComparingTo("1000.00");
        assertThat(ap.status()).isEqualTo(AccountsPayable.ApStatus.PAID);
        assertThat(invoiceStatus(invoiceId)).isEqualTo(InvoiceStatus.PAID);
        assertThat(accountsPayableUseCase.supplierOutstanding(supplierId)).isEqualByComparingTo("0.00");
    }

    @Test
    void overpaymentBeyondRemainingBalanceIsRejectedAndLeavesStateUntouched() {
        var invoiceId = reconciledInvoice(new BigDecimal("1000.00"));

        pay(invoiceId, "600.00");

        // The real A1 scenario: total is 1000, but only 400 remains outstanding.
        assertThatThrownBy(() -> pay(invoiceId, "500.00"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo("OVERPAYMENT");

        var ap = accountsPayable(invoiceId);
        assertThat(ap.paidAmount()).isEqualByComparingTo("600.00");
        assertThat(ap.outstanding()).isEqualByComparingTo("400.00");
    }

    @Test
    void invoiceAndPaymentResponsesExposeTheRealSupplierName() {
        var total = new BigDecimal("1000.00");
        var created = supplierInvoiceUseCase.create(new SupplierInvoiceRequest(
                supplierId,
                "FAC-NAME-" + UUID.randomUUID().toString().substring(0, 8),
                LocalDate.now(), LocalDate.now().plusDays(30),
                total, BigDecimal.ZERO, BigDecimal.ZERO, total,
                null, List.of()), TEST_USER_ID);

        assertThat(created.supplierName()).isEqualTo(SUPPLIER_NAME);
        assertThat(supplierInvoiceUseCase.getById(created.id()).supplierName()).isEqualTo(SUPPLIER_NAME);
        assertThat(supplierInvoiceUseCase.findBySupplier(supplierId, PageRequest.of(0, 20)).content())
                .extracting(SupplierInvoiceResponse::supplierName)
                .containsOnly(SUPPLIER_NAME);

        var payment = pay(reconciledInvoice(total), "1000.00");

        assertThat(payment.supplierName()).isEqualTo(SUPPLIER_NAME);
        assertThat(paymentUseCase.getById(payment.id()).supplierName()).isEqualTo(SUPPLIER_NAME);
        assertThat(paymentUseCase.findBySupplier(supplierId, PageRequest.of(0, 20)).content())
                .extracting(PaymentResponse::supplierName)
                .containsOnly(SUPPLIER_NAME);
    }

    @Test
    void multiInvoicePaymentReducesBothAccountsPayable() {
        var invoiceA = reconciledInvoice(new BigDecimal("600.00"));
        var invoiceB = reconciledInvoice(new BigDecimal("400.00"));

        paymentUseCase.create(new PaymentRequest(
                supplierId, new BigDecimal("600.00"), LocalDate.now(), "TRANSFERENCIA",
                "PAG-MULTI-" + UUID.randomUUID().toString().substring(0, 6), null,
                List.of(
                        new PaymentRequest.InvoicePaymentInput(invoiceA, new BigDecimal("300.00")),
                        new PaymentRequest.InvoicePaymentInput(invoiceB, new BigDecimal("300.00")))),
                TEST_USER_ID);

        assertThat(accountsPayable(invoiceA).outstanding()).isEqualByComparingTo("300.00");
        assertThat(accountsPayable(invoiceB).outstanding()).isEqualByComparingTo("100.00");
        assertThat(accountsPayableUseCase.supplierOutstanding(supplierId)).isEqualByComparingTo("400.00");
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private UUID createSupplier() {
        var supplier = new ThirdPartyEntity();
        supplier.setNumIdentification("SUP-CXP-" + UUID.randomUUID().toString().substring(0, 8));
        supplier.setName("Supplier CxP Payment");
        supplier.setType(ThirdParty.ThirdPartyType.SUPPLIER);
        supplier.setPersonType(ThirdParty.PersonType.JURIDICA);
        supplier.setTaxRegime(ThirdParty.TaxRegime.ORDINARIO);
        supplier.setTaxResponsibilities(List.of());
        supplier.setActive(true);
        supplier.setCreditLimit(BigDecimal.ZERO);
        em.persist(supplier);
        return supplier.getId();
    }

    private UUID reconciledInvoice(BigDecimal total) {
        // A9: reconcile now requires an OC with goods actually received for at least the invoiced
        // amount, so each fixture invoice is backed by its own fully received OC of value == total.
        var ocId = receivedPurchaseOrder(total);
        var created = supplierInvoiceUseCase.create(new SupplierInvoiceRequest(
                supplierId,
                "FAC-CXP-" + UUID.randomUUID().toString().substring(0, 8),
                LocalDate.now(), LocalDate.now().plusDays(30),
                total, BigDecimal.ZERO, BigDecimal.ZERO, total,
                null, List.of(ocId)), TEST_USER_ID);
        supplierInvoiceUseCase.reconcile(created.id());
        return created.id();
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
                    "OC-CXP-" + UUID.randomUUID().toString().substring(0, 6),
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

    private PaymentResponse pay(UUID invoiceId, String amount) {
        return paymentUseCase.create(new PaymentRequest(
                supplierId, new BigDecimal(amount), LocalDate.now(), "TRANSFERENCIA",
                "PAG-CXP-" + UUID.randomUUID().toString().substring(0, 6), null,
                List.of(new PaymentRequest.InvoicePaymentInput(invoiceId, new BigDecimal(amount)))),
                TEST_USER_ID);
    }

    private AccountsPayable accountsPayable(UUID invoiceId) {
        return accountsPayableRepository.findByDocumentId(invoiceId).orElseThrow();
    }

    private InvoiceStatus invoiceStatus(UUID invoiceId) {
        // getById maps the invoice inside its read-only transaction (the adapter lazily loads ocIds).
        return supplierInvoiceUseCase.getById(invoiceId).status();
    }
}
