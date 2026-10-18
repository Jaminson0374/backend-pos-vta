package co.posinvent.integration;

import co.posinvent.application.dto.CustomerReceiptRequest;
import co.posinvent.application.dto.PaymentRequest;
import co.posinvent.application.usecase.CustomerReceiptUseCase;
import co.posinvent.application.usecase.PaymentUseCase;
import co.posinvent.domain.model.InvoiceStatus;
import co.posinvent.domain.model.SalesDocumentStatus;
import co.posinvent.domain.model.SalesDocumentType;
import co.posinvent.domain.model.ThirdParty;
import co.posinvent.domain.model.Warehouse;
import co.posinvent.domain.repository.PucAccountRepository;
import co.posinvent.infrastructure.adapters.out.persistence.AccountsReceivableEntity;
import co.posinvent.infrastructure.adapters.out.persistence.JournalEntryEntity;
import co.posinvent.infrastructure.adapters.out.persistence.JournalEntryLineEntity;
import co.posinvent.infrastructure.adapters.out.persistence.SalesDocumentEntity;
import co.posinvent.infrastructure.adapters.out.persistence.SupplierInvoiceEntity;
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
 * Integration guard for the collections/supplier-payment journal entries (Contabilidad Fase 2, WU-36a).
 *
 * <p>C3: a customer receipt (cobro) posts a cash/receivable entry (debit caja 1105 / credit cartera 1305)
 * dated with the receipt, not with "today". C4: a supplier payment posts the mirror entry
 * (debit payable 2205 / credit caja 1105) dated with the payment.</p>
 *
 * <p>Deliberately NOT {@code @Transactional}: fixtures are committed through a {@link TransactionTemplate}
 * so the use case commits its own transaction and the {@code AFTER_COMMIT} accounting listener runs
 * exactly like in production, leaving the journal entry readable afterwards.</p>
 */
class CollectionsPaymentsJournalIntegrationTest extends AbstractIntegrationTest {

    private static final BigDecimal RECEIPT_AMOUNT = new BigDecimal("150000.00");
    private static final BigDecimal PAYMENT_AMOUNT = new BigDecimal("80000.00");
    // Dates deliberately not "today" so the test proves the document date is used.
    private static final LocalDate RECEIPT_DATE = LocalDate.of(2024, 6, 15);
    private static final LocalDate PAYMENT_DATE = LocalDate.of(2024, 6, 20);

    @Autowired private CustomerReceiptUseCase customerReceiptUseCase;
    @Autowired private PaymentUseCase paymentUseCase;
    @Autowired private PucAccountRepository pucRepo;

    private UUID clientId;
    private UUID arId;
    private UUID supplierId;
    private UUID invoiceId;

    @BeforeEach
    void setUp() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            var warehouseId = TestDataFactory.createWarehouse(
                    em, "COLLPAY WU36", Warehouse.WarehouseType.CORTES);

            // ── collection (C3) fixture: client + a sales document anchoring the AR row ──
            clientId = TestDataFactory.createClientThirdParty(
                    em, "CP-" + UUID.randomUUID().toString().substring(0, 8), "Collections Client");

            var anchorDoc = new SalesDocumentEntity();
            anchorDoc.setType(SalesDocumentType.ORDER);
            anchorDoc.setStatus(SalesDocumentStatus.CONFIRMED);
            anchorDoc.setDocumentNumber("AR-ANCHOR-" + UUID.randomUUID().toString().substring(0, 6));
            anchorDoc.setClientId(clientId);
            anchorDoc.setWarehouseId(warehouseId);
            anchorDoc.setTotalNet(BigDecimal.ZERO);
            anchorDoc.setTotalTax0(BigDecimal.ZERO);
            anchorDoc.setTotalTax5(BigDecimal.ZERO);
            anchorDoc.setTotalTax19(BigDecimal.ZERO);
            anchorDoc.setTotalAmount(BigDecimal.ZERO);
            em.persist(anchorDoc);

            var ar = new AccountsReceivableEntity();
            ar.setClientId(clientId);
            ar.setDocumentId(anchorDoc.getId());
            ar.setTotalAmount(RECEIPT_AMOUNT);
            ar.setPaidAmount(BigDecimal.ZERO);
            ar.setOutstanding(RECEIPT_AMOUNT);
            ar.setDueDate(LocalDate.now().plusDays(30));
            ar.setStatus("OPEN");
            em.persist(ar);

            // ── supplier payment (C4) fixture: supplier + a RECONCILED invoice ──
            var supplier = new ThirdPartyEntity();
            supplier.setNumIdentification("SUP-" + UUID.randomUUID().toString().substring(0, 8));
            supplier.setName("Supplier WU36");
            supplier.setType(ThirdParty.ThirdPartyType.SUPPLIER);
            supplier.setPersonType(ThirdParty.PersonType.JURIDICA);
            supplier.setTaxRegime(ThirdParty.TaxRegime.ORDINARIO);
            supplier.setTaxResponsibilities(List.of());
            supplier.setActive(true);
            supplier.setCreditLimit(BigDecimal.ZERO);
            em.persist(supplier);

            var invoice = new SupplierInvoiceEntity();
            invoice.setId(UUID.randomUUID());
            invoice.setSupplierId(supplier.getId());
            invoice.setInvoiceNumber("FAC-WU36-" + UUID.randomUUID().toString().substring(0, 6));
            invoice.setIssueDate(LocalDate.now().minusDays(10));
            invoice.setDueDate(LocalDate.now().plusDays(20));
            invoice.setSubtotal(PAYMENT_AMOUNT);
            invoice.setIvaTotal(BigDecimal.ZERO);
            invoice.setRetentionTotal(BigDecimal.ZERO);
            invoice.setTotal(PAYMENT_AMOUNT);
            invoice.setStatus(InvoiceStatus.RECONCILED);
            invoice.setCreatedBy(TEST_USER_ID);
            em.persist(invoice);

            em.flush();

            arId = ar.getId();
            supplierId = supplier.getId();
            invoiceId = invoice.getId();
        });
    }

    @Test
    void customerReceiptPostsBalancedCashDebitReceivableCreditDatedWithTheReceipt() {
        var request = new CustomerReceiptRequest(
                clientId, RECEIPT_AMOUNT, RECEIPT_DATE, "CASH", "REC-WU36-1", null,
                List.of(new CustomerReceiptRequest.ArApplicationInput(arId, RECEIPT_AMOUNT)));

        var response = customerReceiptUseCase.create(request);

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            var cashId = pucRepo.findByCode("1105").orElseThrow().id();
            var receivableId = pucRepo.findByCode("1305").orElseThrow().id();

            var entries = entriesFor("PAYMENT", response.id());
            assertThat(entries).hasSize(1);
            var entry = entries.get(0);

            // The entry is dated with the receipt, not with "today".
            assertThat(entry.getEntryDate()).isEqualTo(RECEIPT_DATE);

            assertThat(totalDebit(entry)).isEqualByComparingTo(totalCredit(entry));
            assertThat(totalDebit(entry)).isEqualByComparingTo(RECEIPT_AMOUNT);

            var cash = lineOn(entry, cashId);
            assertThat(cash.getDebit()).isEqualByComparingTo(RECEIPT_AMOUNT);
            assertThat(cash.getCredit()).isEqualByComparingTo(BigDecimal.ZERO);

            var receivable = lineOn(entry, receivableId);
            assertThat(receivable.getCredit()).isEqualByComparingTo(RECEIPT_AMOUNT);
            assertThat(receivable.getDebit()).isEqualByComparingTo(BigDecimal.ZERO);
        });
    }

    @Test
    void supplierPaymentPostsBalancedPayableDebitCashCreditDatedWithThePayment() {
        var request = new PaymentRequest(
                supplierId, PAYMENT_AMOUNT, PAYMENT_DATE, "TRANSFERENCIA", "PAG-WU36-1", null,
                List.of(new PaymentRequest.InvoicePaymentInput(invoiceId, PAYMENT_AMOUNT)));

        var response = paymentUseCase.create(request, TEST_USER_ID);

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            var payableId = pucRepo.findByCode("2205").orElseThrow().id();
            var cashId = pucRepo.findByCode("1105").orElseThrow().id();

            var entries = entriesFor("PAYMENT", response.id());
            assertThat(entries).hasSize(1);
            var entry = entries.get(0);

            // The entry is dated with the payment, not with "today".
            assertThat(entry.getEntryDate()).isEqualTo(PAYMENT_DATE);

            assertThat(totalDebit(entry)).isEqualByComparingTo(totalCredit(entry));
            assertThat(totalDebit(entry)).isEqualByComparingTo(PAYMENT_AMOUNT);

            var payable = lineOn(entry, payableId);
            assertThat(payable.getDebit()).isEqualByComparingTo(PAYMENT_AMOUNT);
            assertThat(payable.getCredit()).isEqualByComparingTo(BigDecimal.ZERO);

            var cash = lineOn(entry, cashId);
            assertThat(cash.getCredit()).isEqualByComparingTo(PAYMENT_AMOUNT);
            assertThat(cash.getDebit()).isEqualByComparingTo(BigDecimal.ZERO);
        });
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private List<JournalEntryEntity> entriesFor(String sourceType, UUID sourceId) {
        return em.createQuery(
                        "select e from JournalEntryEntity e "
                                + "where e.sourceType = :sourceType and e.sourceId = :sourceId",
                        JournalEntryEntity.class)
                .setParameter("sourceType", sourceType)
                .setParameter("sourceId", sourceId)
                .getResultList();
    }

    private static JournalEntryLineEntity lineOn(JournalEntryEntity entry, UUID accountId) {
        return entry.getLines().stream()
                .filter(l -> accountId.equals(l.getAccountId()))
                .findFirst().orElseThrow();
    }

    private static BigDecimal totalDebit(JournalEntryEntity entry) {
        return entry.getLines().stream()
                .map(JournalEntryLineEntity::getDebit)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal totalCredit(JournalEntryEntity entry) {
        return entry.getLines().stream()
                .map(JournalEntryLineEntity::getCredit)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
