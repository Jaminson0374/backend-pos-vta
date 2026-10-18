package co.posinvent.integration;

import co.posinvent.application.dto.GoodsReceiptRequest;
import co.posinvent.application.dto.SupplierInvoiceRequest;
import co.posinvent.application.usecase.AccountsPayableUseCase;
import co.posinvent.application.usecase.CreateGoodsReceiptUseCase;
import co.posinvent.application.usecase.SupplierInvoiceUseCase;
import co.posinvent.domain.model.AccountsPayable;
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
 * Guards that the supplier payable balance is derived exclusively from
 * {@code accounts_payable.outstanding}.
 *
 * <p>Reconciles an invoice through the real use cases and asserts the derived balance matches the
 * CxP. The legacy static balance column was removed in A6, so the CxP is the only payable source.</p>
 *
 * <p>Deliberately NOT {@code @Transactional}: the use cases commit their own transactions so the
 * reconciliation and its AFTER_COMMIT listeners behave like production.</p>
 */
class SupplierBalanceDerivationIntegrationTest extends AbstractIntegrationTest {

    private static final BigDecimal INVOICE_TOTAL = new BigDecimal("1000.00");

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
            supplier.setNumIdentification("SUP-DERIVED-" + UUID.randomUUID().toString().substring(0, 8));
            supplier.setName("Supplier Derived Balance");
            supplier.setType(ThirdParty.ThirdPartyType.SUPPLIER);
            supplier.setPersonType(ThirdParty.PersonType.JURIDICA);
            supplier.setTaxRegime(ThirdParty.TaxRegime.ORDINARIO);
            supplier.setTaxResponsibilities(List.of());
            supplier.setActive(true);
            supplier.setCreditLimit(BigDecimal.ZERO);
            em.persist(supplier);
            supplierId = supplier.getId();

            warehouseId = TestDataFactory.createWarehouse(
                    em, "Derived Balance WH", Warehouse.WarehouseType.CORTES);
            productId = TestDataFactory.createProduct(
                    em, "DERIV", "Producto saldo derivado", false, true);
        });
    }

    @Test
    void supplierOutstandingComesFromTheCxP() {
        var invoiceId = reconciledInvoice(INVOICE_TOTAL);

        var ap = ap(invoiceId);
        assertThat(ap.outstanding()).isEqualByComparingTo(INVOICE_TOTAL);

        var derived = accountsPayableUseCase.supplierOutstanding(supplierId);
        assertThat(derived).isEqualByComparingTo(INVOICE_TOTAL);
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private UUID reconciledInvoice(BigDecimal total) {
        var ocId = receivedPurchaseOrder(total);
        var created = supplierInvoiceUseCase.create(new SupplierInvoiceRequest(
                supplierId,
                "FAC-DERIVED-" + UUID.randomUUID().toString().substring(0, 8),
                LocalDate.now(), LocalDate.now().plusDays(30),
                total, BigDecimal.ZERO, BigDecimal.ZERO, total,
                null, List.of(ocId)), TEST_USER_ID);
        supplierInvoiceUseCase.reconcile(created.id());
        return created.id();
    }

    private UUID receivedPurchaseOrder(BigDecimal value) {
        var holder = new PurchaseOrder[1];
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            var line = new PurchaseLineItem(
                    null, null, productId, warehouseId,
                    value, BigDecimal.ZERO, BigDecimal.ONE,
                    BigDecimal.ZERO, "EXENTO", 1);
            holder[0] = purchaseOrderRepository.save(new PurchaseOrder(
                    null, supplierId, PurchaseOrderStatus.PENDING, LocalDate.now(),
                    "OC-DERIVED-" + UUID.randomUUID().toString().substring(0, 6),
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
}
