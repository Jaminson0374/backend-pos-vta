package co.posinvent.integration;

import co.posinvent.application.dto.AdvanceRequest;
import co.posinvent.application.dto.ApplyAdvanceRequest;
import co.posinvent.application.dto.GoodsReceiptRequest;
import co.posinvent.application.dto.SupplierInvoiceRequest;
import co.posinvent.application.usecase.ApplyAdvanceUseCase;
import co.posinvent.application.usecase.CreateGoodsReceiptUseCase;
import co.posinvent.application.usecase.PaymentUseCase;
import co.posinvent.application.usecase.SupplierBalanceUseCase;
import co.posinvent.application.usecase.SupplierInvoiceUseCase;
import co.posinvent.domain.model.PurchaseLineItem;
import co.posinvent.domain.model.PurchaseOrder;
import co.posinvent.domain.model.PurchaseOrderStatus;
import co.posinvent.domain.model.ThirdParty;
import co.posinvent.domain.model.Warehouse;
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
 * Integration guard for S3a: the supplier net position treats an unapplied advance as an asset.
 *
 * <p>{@code netPosition = CxP outstanding - unapplied advances}. With a 600 payable and a 1000
 * advance the position is -400 (credit in our favor). Applying 600 of the advance settles the CxP
 * and leaves 400 of advance, so the net position stays -400.</p>
 *
 * <p>Deliberately NOT {@code @Transactional}: fixtures and the use cases commit their own
 * transactions so the AFTER_COMMIT accounting listeners run like in production.</p>
 */
class SupplierBalanceNetPositionIntegrationTest extends AbstractIntegrationTest {

    @Autowired private SupplierBalanceUseCase supplierBalanceUseCase;
    @Autowired private PaymentUseCase paymentUseCase;
    @Autowired private ApplyAdvanceUseCase applyAdvanceUseCase;
    @Autowired private SupplierInvoiceUseCase supplierInvoiceUseCase;
    @Autowired private CreateGoodsReceiptUseCase createGoodsReceiptUseCase;
    @Autowired private PurchaseOrderRepository purchaseOrderRepository;

    private UUID supplierId;
    private UUID warehouseId;
    private UUID productId;

    @BeforeEach
    void setUp() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            var supplier = new ThirdPartyEntity();
            supplier.setNumIdentification("SUP-NET-" + UUID.randomUUID().toString().substring(0, 8));
            supplier.setName("Supplier Net Position");
            supplier.setType(ThirdParty.ThirdPartyType.SUPPLIER);
            supplier.setPersonType(ThirdParty.PersonType.JURIDICA);
            supplier.setTaxRegime(ThirdParty.TaxRegime.ORDINARIO);
            supplier.setTaxResponsibilities(List.of());
            supplier.setActive(true);
            supplier.setCreditLimit(BigDecimal.ZERO);
            em.persist(supplier);
            supplierId = supplier.getId();

            warehouseId = TestDataFactory.createWarehouse(
                    em, "Net Position WH", Warehouse.WarehouseType.CORTES);
            productId = TestDataFactory.createProduct(
                    em, "NETP", "Producto saldo neto", false, true);
        });
    }

    @Test
    void advanceIsAnAssetAndTheNetPositionKeepsItsSignAfterApplying() {
        var invoiceId = reconciledInvoice(new BigDecimal("600.00"));
        var advanceId = advance("1000.00");

        var before = supplierBalanceUseCase.getBalance(supplierId);
        assertThat(before.supplierId()).isEqualTo(supplierId);
        assertThat(before.payableOutstanding()).isEqualByComparingTo("600.00");
        assertThat(before.advanceBalance()).isEqualByComparingTo("1000.00");
        assertThat(before.netPosition()).isEqualByComparingTo("-400.00");

        applyAdvanceUseCase.apply(
                new ApplyAdvanceRequest(advanceId, invoiceId, new BigDecimal("600.00")), TEST_USER_ID);

        var after = supplierBalanceUseCase.getBalance(supplierId);
        assertThat(after.payableOutstanding()).isEqualByComparingTo("0.00");
        assertThat(after.advanceBalance()).isEqualByComparingTo("400.00");
        assertThat(after.netPosition()).isEqualByComparingTo("-400.00");
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private UUID advance(String amount) {
        return paymentUseCase.createAdvance(new AdvanceRequest(
                supplierId, new BigDecimal(amount), LocalDate.now(), "TRANSFERENCIA",
                "ANT-NET-" + UUID.randomUUID().toString().substring(0, 6), null),
                TEST_USER_ID).id();
    }

    /** A reconciled invoice (and therefore a CxP) backed by a fully received purchase order. */
    private UUID reconciledInvoice(BigDecimal total) {
        var ocId = receivedPurchaseOrder(total);
        var invoiceId = supplierInvoiceUseCase.create(new SupplierInvoiceRequest(
                supplierId,
                "FAC-NET-" + UUID.randomUUID().toString().substring(0, 8),
                LocalDate.now(), LocalDate.now().plusDays(30),
                total, BigDecimal.ZERO, BigDecimal.ZERO, total,
                null, List.of(ocId)), TEST_USER_ID).id();
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
                    "OC-NET-" + UUID.randomUUID().toString().substring(0, 6),
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
}
