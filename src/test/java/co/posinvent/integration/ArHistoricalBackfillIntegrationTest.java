package co.posinvent.integration;

import co.posinvent.domain.model.SalesDocumentStatus;
import co.posinvent.domain.model.SalesDocumentType;
import co.posinvent.domain.model.Warehouse;
import co.posinvent.infrastructure.adapters.out.persistence.AccountsReceivableEntity;
import co.posinvent.infrastructure.adapters.out.persistence.SalesDocumentEntity;
import co.posinvent.infrastructure.adapters.out.persistence.TestDataFactory;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the historical AR backfill and its follow-up realignment (work units A6-1
 * and A6-1b) by executing the real Flyway migrations against Testcontainers PostgreSQL.
 *
 * <p>Before A4 a credit-sale devolution reduced {@code outstanding} without reducing
 * {@code total_amount}. A later receipt recomputed {@code outstanding = total_amount - paid_amount}
 * and re-inflated the debt. V134 restores {@code total_amount = paid_amount + outstanding}
 * for receivables with an associated credit note. V136 then derives the authoritative total from
 * the source documents (the invoice plus its {@code ISSUED} credit notes), because a row
 * re-inflated by a receipt already satisfies the V134 invariant.</p>
 */
class ArHistoricalBackfillIntegrationTest extends AbstractIntegrationTest {

    private static final Path V134_MIGRATION = Path.of(
            "src/main/resources/db/migration/V134__backfill_accounts_receivable_totals.sql");
    private static final Path V136_MIGRATION = Path.of(
            "src/main/resources/db/migration/"
                    + "V136__realign_accounts_receivable_from_source_documents.sql");

    private static final BigDecimal ZERO = new BigDecimal("0.00");
    private static final BigDecimal HUNDRED = new BigDecimal("100.00");
    private static final BigDecimal TWO_HUNDRED = new BigDecimal("200.00");
    private static final BigDecimal THREE_HUNDRED = new BigDecimal("300.00");
    private static final BigDecimal FOUR_HUNDRED = new BigDecimal("400.00");
    private static final BigDecimal SIX_HUNDRED = new BigDecimal("600.00");
    private static final BigDecimal NINE_HUNDRED = new BigDecimal("900.00");
    private static final BigDecimal THOUSAND = new BigDecimal("1000.00");
    private static final BigDecimal MINUS_SIX_HUNDRED = new BigDecimal("-600.00");
    private static final BigDecimal MINUS_THOUSAND = new BigDecimal("-1000.00");

    @Test
    void repairsMisalignedReceivablesWithAnAssociatedCreditNote() throws IOException {
        var seeded = seedV134Fixture();
        String sql = executableSql(V134_MIGRATION);

        int updated = new TransactionTemplate(transactionManager).execute(status ->
                em.createNativeQuery(sql).executeUpdate());

        assertThat(updated)
                .as("the four misaligned receivables with a credit note must be repaired")
                .isGreaterThanOrEqualTo(4);

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            em.clear();

            assertReceivable(seeded.get("open-unpaid"), "400.00", "0.00", "400.00", "OPEN");
            assertReceivable(seeded.get("fully-returned"), "0.00", "0.00", "0.00", "PAID");
            assertReceivable(seeded.get("partial"), "500.00", "200.00", "300.00", "PARTIAL");
            assertReceivable(seeded.get("control-no-credit-note"), "1000.00", "0.00", "400.00", "OPEN");
            assertReceivable(seeded.get("overdue"), "400.00", "0.00", "400.00", "OVERDUE");
        });
    }

    @Test
    void realignsReceivableTotalsFromSourceDocuments() throws IOException {
        var seeded = seedV136Fixture();
        String sql = executableSql(V136_MIGRATION);

        new TransactionTemplate(transactionManager).execute(status ->
                em.createNativeQuery(sql).executeUpdate());

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            em.clear();

            // Already satisfied total = paid + outstanding, so V134 alone left it inflated.
            assertReceivable(seeded.get("aligned-but-inflated"), "400.00", "400.00", "0.00", "PAID");
            assertReceivable(seeded.get("misaligned"), "400.00", "0.00", "400.00", "OPEN");
            assertReceivable(seeded.get("partial-paid"), "400.00", "100.00", "300.00", "PARTIAL");
            assertReceivable(seeded.get("overdue"), "400.00", "0.00", "400.00", "OVERDUE");
            assertReceivable(seeded.get("draft-note-control"), "1000.00", "400.00", "600.00", "OPEN");
        });
    }

    private Map<String, UUID> seedV134Fixture() {
        return new TransactionTemplate(transactionManager).execute(status -> {
            UUID warehouseId = TestDataFactory.createWarehouse(
                    em, "AR BACKFILL " + shortId(), Warehouse.WarehouseType.CORTES);
            UUID clientId = TestDataFactory.createClientThirdParty(
                    em, "ARBF-" + shortId(), "AR Backfill Client");

            Map<String, UUID> seeded = new LinkedHashMap<>();

            // Case 1: unpaid but partially returned, still owes 400.
            seeded.put("open-unpaid", seedReceivable(
                    clientId, warehouseId, THOUSAND, MINUS_SIX_HUNDRED, SalesDocumentStatus.ISSUED,
                    THOUSAND, ZERO, FOUR_HUNDRED, "OPEN"));

            // Case 2: fully returned, nothing owed.
            seeded.put("fully-returned", seedReceivable(
                    clientId, warehouseId, THOUSAND, MINUS_THOUSAND, SalesDocumentStatus.ISSUED,
                    THOUSAND, ZERO, ZERO, "OPEN"));

            // Case 3: partially paid and partially returned.
            seeded.put("partial", seedReceivable(
                    clientId, warehouseId, THOUSAND, MINUS_SIX_HUNDRED, SalesDocumentStatus.ISSUED,
                    THOUSAND, TWO_HUNDRED, THREE_HUNDRED, "PARTIAL"));

            // Case 4: control row, no credit note, must stay untouched.
            seeded.put("control-no-credit-note", seedReceivable(
                    clientId, warehouseId, THOUSAND, null, null,
                    THOUSAND, ZERO, FOUR_HUNDRED, "OPEN"));

            // Case 5: overdue status must survive the repair while money is owed.
            seeded.put("overdue", seedReceivable(
                    clientId, warehouseId, THOUSAND, MINUS_SIX_HUNDRED, SalesDocumentStatus.ISSUED,
                    THOUSAND, ZERO, FOUR_HUNDRED, "OVERDUE"));

            em.flush();
            return seeded;
        });
    }

    private Map<String, UUID> seedV136Fixture() {
        return new TransactionTemplate(transactionManager).execute(status -> {
            UUID warehouseId = TestDataFactory.createWarehouse(
                    em, "AR REALIGN " + shortId(), Warehouse.WarehouseType.CORTES);
            UUID clientId = TestDataFactory.createClientThirdParty(
                    em, "ARRL-" + shortId(), "AR Realign Client");

            Map<String, UUID> seeded = new LinkedHashMap<>();

            // total = paid + outstanding, but the total itself is inflated by a later receipt.
            seeded.put("aligned-but-inflated", seedReceivable(
                    clientId, warehouseId, THOUSAND, MINUS_SIX_HUNDRED, SalesDocumentStatus.ISSUED,
                    THOUSAND, FOUR_HUNDRED, SIX_HUNDRED, "OPEN"));

            // Classic misalignment: total never absorbed the credit note.
            seeded.put("misaligned", seedReceivable(
                    clientId, warehouseId, THOUSAND, MINUS_SIX_HUNDRED, SalesDocumentStatus.ISSUED,
                    THOUSAND, ZERO, FOUR_HUNDRED, "OPEN"));

            // Partially paid, inflated outstanding.
            seeded.put("partial-paid", seedReceivable(
                    clientId, warehouseId, THOUSAND, MINUS_SIX_HUNDRED, SalesDocumentStatus.ISSUED,
                    THOUSAND, HUNDRED, NINE_HUNDRED, "PARTIAL"));

            // Overdue status must survive while money is still owed.
            seeded.put("overdue", seedReceivable(
                    clientId, warehouseId, THOUSAND, MINUS_SIX_HUNDRED, SalesDocumentStatus.ISSUED,
                    THOUSAND, ZERO, FOUR_HUNDRED, "OVERDUE"));

            // A DRAFT credit note must not realign anything.
            seeded.put("draft-note-control", seedReceivable(
                    clientId, warehouseId, THOUSAND, MINUS_SIX_HUNDRED, SalesDocumentStatus.DRAFT,
                    THOUSAND, FOUR_HUNDRED, SIX_HUNDRED, "OPEN"));

            em.flush();
            return seeded;
        });
    }

    private UUID seedReceivable(UUID clientId, UUID warehouseId,
                                BigDecimal invoiceTotal,
                                BigDecimal creditNoteTotal,
                                SalesDocumentStatus creditNoteStatus,
                                BigDecimal totalAmount, BigDecimal paidAmount,
                                BigDecimal outstanding, String status) {
        var invoice = new SalesDocumentEntity();
        invoice.setType(SalesDocumentType.INVOICE);
        invoice.setStatus(SalesDocumentStatus.CONFIRMED);
        invoice.setDocumentNumber("ARBF-ANCHOR-" + shortId());
        invoice.setClientId(clientId);
        invoice.setWarehouseId(warehouseId);
        invoice.setTotalNet(invoiceTotal);
        invoice.setTotalTax0(ZERO);
        invoice.setTotalTax5(ZERO);
        invoice.setTotalTax19(ZERO);
        invoice.setTotalAmount(invoiceTotal);
        em.persist(invoice);
        em.flush();

        var ar = new AccountsReceivableEntity();
        ar.setClientId(clientId);
        ar.setDocumentId(invoice.getId());
        ar.setTotalAmount(totalAmount);
        ar.setPaidAmount(paidAmount);
        ar.setOutstanding(outstanding);
        ar.setDueDate(LocalDate.now().plusDays(30));
        ar.setStatus(status);
        em.persist(ar);

        if (creditNoteTotal != null) {
            var creditNote = new SalesDocumentEntity();
            creditNote.setType(SalesDocumentType.CREDIT_NOTE);
            creditNote.setStatus(creditNoteStatus);
            creditNote.setDocumentNumber("ARBF-CN-" + shortId());
            creditNote.setClientId(clientId);
            creditNote.setWarehouseId(warehouseId);
            creditNote.setSourceDocumentId(invoice.getId());
            creditNote.setTotalNet(creditNoteTotal);
            creditNote.setTotalTax0(ZERO);
            creditNote.setTotalTax5(ZERO);
            creditNote.setTotalTax19(ZERO);
            creditNote.setTotalAmount(creditNoteTotal);
            em.persist(creditNote);
        }

        return ar.getId();
    }

    private void assertReceivable(UUID arId, String expectedTotal, String expectedPaid,
                                  String expectedOutstanding, String expectedStatus) {
        var ar = em.find(AccountsReceivableEntity.class, arId);

        assertThat(ar).isNotNull();
        assertThat(ar.getTotalAmount()).isEqualByComparingTo(expectedTotal);
        assertThat(ar.getPaidAmount()).isEqualByComparingTo(expectedPaid);
        assertThat(ar.getOutstanding()).isEqualByComparingTo(expectedOutstanding);
        assertThat(ar.getStatus()).isEqualTo(expectedStatus);
    }

    /**
     * Reads the actual migration file so the test cannot drift from a copied SQL string.
     * JDBC does not accept leading {@code --} comment lines, so they are stripped before
     * execution; the migration file itself is never modified.
     */
    private static String executableSql(Path migration) throws IOException {
        return Files.readString(migration)
                .lines()
                .filter(line -> !line.stripLeading().startsWith("--"))
                .collect(Collectors.joining("\n"));
    }

    private static String shortId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

}
