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
 * Verifies the historical AR backfill (work unit A6-1) by executing the real Flyway
 * migration against Testcontainers PostgreSQL.
 *
 * <p>Before A4 a credit-sale devolution reduced {@code outstanding} without reducing
 * {@code total_amount}. A later receipt recomputed {@code outstanding = total_amount - paid_amount}
 * and re-inflated the debt. The migration restores {@code total_amount = paid_amount + outstanding}
 * only for receivables that have an associated {@code CREDIT_NOTE} and a misaligned total.</p>
 */
class ArHistoricalBackfillIntegrationTest extends AbstractIntegrationTest {

    private static final Path MIGRATION = Path.of(
            "src/main/resources/db/migration/V134__backfill_accounts_receivable_totals.sql");

    private static final BigDecimal THOUSAND = new BigDecimal("1000.00");
    private static final BigDecimal FOUR_HUNDRED = new BigDecimal("400.00");

    @Test
    void repairsOnlyMisalignedReceivablesWithAnAssociatedCreditNote() throws IOException {
        var seeded = seedFixture();
        String sql = executableMigrationSql();

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

    private Map<String, UUID> seedFixture() {
        return new TransactionTemplate(transactionManager).execute(status -> {
            UUID warehouseId = TestDataFactory.createWarehouse(
                    em, "AR BACKFILL " + shortId(), Warehouse.WarehouseType.CORTES);
            UUID clientId = TestDataFactory.createClientThirdParty(
                    em, "ARBF-" + shortId(), "AR Backfill Client");

            Map<String, UUID> seeded = new LinkedHashMap<>();

            // Case 1: unpaid but partially returned, still owes 400.
            seeded.put("open-unpaid", seedReceivable(
                    clientId, warehouseId, THOUSAND,
                    BigDecimal.ZERO, FOUR_HUNDRED, "OPEN", true));

            // Case 2: fully returned, nothing owed.
            seeded.put("fully-returned", seedReceivable(
                    clientId, warehouseId, THOUSAND,
                    BigDecimal.ZERO, BigDecimal.ZERO, "OPEN", true));

            // Case 3: partially paid and partially returned.
            seeded.put("partial", seedReceivable(
                    clientId, warehouseId, THOUSAND,
                    new BigDecimal("200.00"), new BigDecimal("300.00"), "PARTIAL", true));

            // Case 4: control row, no credit note, must stay untouched.
            seeded.put("control-no-credit-note", seedReceivable(
                    clientId, warehouseId, THOUSAND,
                    BigDecimal.ZERO, FOUR_HUNDRED, "OPEN", false));

            // Case 5: overdue status must survive the repair while money is owed.
            seeded.put("overdue", seedReceivable(
                    clientId, warehouseId, THOUSAND,
                    BigDecimal.ZERO, FOUR_HUNDRED, "OVERDUE", true));

            em.flush();
            return seeded;
        });
    }

    private UUID seedReceivable(UUID clientId, UUID warehouseId,
                                    BigDecimal totalAmount, BigDecimal paidAmount,
                                    BigDecimal outstanding, String status,
                                    boolean withCreditNote) {
        var anchor = new SalesDocumentEntity();
        anchor.setType(SalesDocumentType.ORDER);
        anchor.setStatus(SalesDocumentStatus.CONFIRMED);
        anchor.setDocumentNumber("ARBF-ANCHOR-" + shortId());
        anchor.setClientId(clientId);
        anchor.setWarehouseId(warehouseId);
        anchor.setTotalNet(BigDecimal.ZERO);
        anchor.setTotalTax0(BigDecimal.ZERO);
        anchor.setTotalTax5(BigDecimal.ZERO);
        anchor.setTotalTax19(BigDecimal.ZERO);
        anchor.setTotalAmount(BigDecimal.ZERO);
        em.persist(anchor);
        em.flush();

        var ar = new AccountsReceivableEntity();
        ar.setClientId(clientId);
        ar.setDocumentId(anchor.getId());
        ar.setTotalAmount(totalAmount);
        ar.setPaidAmount(paidAmount);
        ar.setOutstanding(outstanding);
        ar.setDueDate(LocalDate.now().plusDays(30));
        ar.setStatus(status);
        em.persist(ar);

        if (withCreditNote) {
            var creditNote = new SalesDocumentEntity();
            creditNote.setType(SalesDocumentType.CREDIT_NOTE);
            creditNote.setStatus(SalesDocumentStatus.ISSUED);
            creditNote.setDocumentNumber("ARBF-CN-" + shortId());
            creditNote.setClientId(clientId);
            creditNote.setWarehouseId(warehouseId);
            creditNote.setSourceDocumentId(anchor.getId());
            creditNote.setTotalNet(BigDecimal.ZERO);
            creditNote.setTotalTax0(BigDecimal.ZERO);
            creditNote.setTotalTax5(BigDecimal.ZERO);
            creditNote.setTotalTax19(BigDecimal.ZERO);
            creditNote.setTotalAmount(BigDecimal.ZERO);
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
    private static String executableMigrationSql() throws IOException {
        return Files.readString(MIGRATION)
                .lines()
                .filter(line -> !line.stripLeading().startsWith("--"))
                .collect(Collectors.joining("\n"));
    }

    private static String shortId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

}
