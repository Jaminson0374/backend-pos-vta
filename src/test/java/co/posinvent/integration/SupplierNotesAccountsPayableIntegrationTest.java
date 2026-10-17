package co.posinvent.integration;

import co.posinvent.application.dto.DebitCreditNoteRequest;
import co.posinvent.application.dto.GoodsReceiptRequest;
import co.posinvent.application.dto.SupplierInvoiceRequest;
import co.posinvent.application.usecase.AccountsPayableUseCase;
import co.posinvent.application.usecase.CreateGoodsReceiptUseCase;
import co.posinvent.application.usecase.ManageDebitCreditNoteUseCase;
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
 * Integration guard for Auditoría Compras hallazgo M2 (WU-45a): a supplier debit/credit note must
 * keep the AccountsPayable ({@code accounts_payable.outstanding}) consistent through its whole
 * lifecycle.
 *
 * <p>Before this WU editing a note never reversed its previous AP effect (double counting) and
 * deleting a note only reversed the static supplier balance, never the CxP. The CxP is the single
 * source of truth, so create/edit/delete must all move it.</p>
 *
 * <p>Deliberately NOT {@code @Transactional}: fixtures and the use cases commit their own
 * transactions so the AFTER_COMMIT accounting listeners run like in production.</p>
 */
class SupplierNotesAccountsPayableIntegrationTest extends AbstractIntegrationTest {

    @Autowired private ManageDebitCreditNoteUseCase manageDebitCreditNoteUseCase;
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
            supplier.setNumIdentification("SUP-NOTE-CXP-" + UUID.randomUUID().toString().substring(0, 8));
            supplier.setName("Supplier Note CxP");
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
                    em, "CxP Note WH", Warehouse.WarehouseType.CORTES);
            productId = TestDataFactory.createProduct(
                    em, "CXPN", "Producto nota CxP", false, true);
        });
    }

    @Test
    void creditNoteCreateEditDeleteKeepsTheCxPConsistent() {
        var invoiceId = reconciledInvoice(new BigDecimal("1000.00"));
        assertThat(ap(invoiceId).totalAmount()).isEqualByComparingTo("1000.00");
        assertThat(ap(invoiceId).outstanding()).isEqualByComparingTo("1000.00");

        var note = manageDebitCreditNoteUseCase.create(new DebitCreditNoteRequest(
                "CREDIT_NOTE", supplierId, invoiceId, null,
                new BigDecimal("200.00"), "Nota crédito M2", null));
        assertThat(ap(invoiceId).totalAmount()).isEqualByComparingTo("800.00");
        assertThat(ap(invoiceId).outstanding()).isEqualByComparingTo("800.00");

        manageDebitCreditNoteUseCase.update(note.id(), new DebitCreditNoteRequest(
                "CREDIT_NOTE", supplierId, invoiceId, null,
                new BigDecimal("300.00"), "Nota crédito M2 editada", null));
        // The old 200 must be reversed before applying the new 300: 1000 - 300 = 700.
        // A buggy double-apply would leave 500; a lost reversal would leave 1000.
        assertThat(ap(invoiceId).totalAmount()).isEqualByComparingTo("700.00");
        assertThat(ap(invoiceId).outstanding()).isEqualByComparingTo("700.00");

        manageDebitCreditNoteUseCase.delete(note.id());
        // Deleting must reverse the CxP effect, not only the static supplier balance.
        assertThat(ap(invoiceId).totalAmount()).isEqualByComparingTo("1000.00");
        assertThat(ap(invoiceId).outstanding()).isEqualByComparingTo("1000.00");
        assertThat(accountsPayableUseCase.supplierOutstanding(supplierId))
                .isEqualByComparingTo("1000.00");
    }

    @Test
    void editingNoteTypeDebitToCreditReversesTheOldEffectThenAppliesTheNew() {
        var invoiceId = reconciledInvoice(new BigDecimal("1000.00"));

        var note = manageDebitCreditNoteUseCase.create(new DebitCreditNoteRequest(
                "DEBIT_NOTE", supplierId, invoiceId, null,
                new BigDecimal("200.00"), "Nota débito M2", null));
        assertThat(ap(invoiceId).totalAmount()).isEqualByComparingTo("1200.00");

        manageDebitCreditNoteUseCase.update(note.id(), new DebitCreditNoteRequest(
                "CREDIT_NOTE", supplierId, invoiceId, null,
                new BigDecimal("200.00"), "Nota crédito M2", null));
        // Reverse the debit (+200 back to 1000) then apply the credit (-200) -> 800.
        assertThat(ap(invoiceId).totalAmount()).isEqualByComparingTo("800.00");
        assertThat(ap(invoiceId).outstanding()).isEqualByComparingTo("800.00");
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private UUID reconciledInvoice(BigDecimal total) {
        // A9: reconcile requires an OC with goods actually received for at least the invoiced
        // amount, so the fixture invoice is backed by its own fully received OC of value == total.
        var ocId = receivedPurchaseOrder(total);
        var created = supplierInvoiceUseCase.create(new SupplierInvoiceRequest(
                supplierId,
                "FAC-NOTE-CXP-" + UUID.randomUUID().toString().substring(0, 8),
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
                    "OC-NOTE-CXP-" + UUID.randomUUID().toString().substring(0, 6),
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
