package co.posinvent.integration;

import co.posinvent.application.usecase.CashCountRequest;
import co.posinvent.application.usecase.CloseShiftUseCase;
import co.posinvent.domain.model.SalesDocumentStatus;
import co.posinvent.domain.model.SalesDocumentType;
import co.posinvent.domain.model.Warehouse;
import co.posinvent.domain.repository.PucAccountRepository;
import co.posinvent.domain.repository.ShiftRepository;
import co.posinvent.infrastructure.adapters.out.persistence.JournalEntryEntity;
import co.posinvent.infrastructure.adapters.out.persistence.JournalEntryLineEntity;
import co.posinvent.infrastructure.adapters.out.persistence.SalesDocumentEntity;
import co.posinvent.infrastructure.adapters.out.persistence.TestDataFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration guard for the SHIFT_CLOSE accounting (Contabilidad Fase 2, WU-36d): when a shift is
 * closed with a cash-count difference, an adjustment entry must be posted against caja {@code 1105}
 * and a faltante/sobrante account. A perfect match ({@code difference == 0}) must post nothing.
 *
 * <p>Deliberately NOT {@code @Transactional}: the shift and its invoice are seeded in their own
 * committed transaction so the close use case runs like a production caller and the
 * {@code AFTER_COMMIT} accounting listener fires exactly once.</p>
 */
class ShiftCloseJournalIntegrationTest extends AbstractIntegrationTest {

    @Autowired private CloseShiftUseCase closeShiftUseCase;
    @Autowired private ShiftRepository shiftRepository;
    @Autowired private PucAccountRepository pucRepo;

    private UUID shiftId;

    @BeforeEach
    void setUp() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            var registerId = (UUID) em.createNativeQuery(
                            "INSERT INTO cash_registers (name) VALUES ('SHIFT-CLOSE-JE') RETURNING id")
                    .getSingleResult();
            shiftId = (UUID) em.createNativeQuery("""
                            INSERT INTO shifts (cash_register_id, user_id, opening_amount, status)
                            VALUES (CAST(:reg AS uuid), CAST(:user AS uuid), 0, 'OPEN')
                            RETURNING id
                            """)
                    .setParameter("reg", registerId.toString())
                    .setParameter("user", TEST_USER_ID.toString())
                    .getSingleResult();

            var warehouseId = TestDataFactory.createWarehouse(em, "SCJE", Warehouse.WarehouseType.CORTES);
            var clientId = TestDataFactory.createClientThirdParty(
                    em, "SCJE-" + UUID.randomUUID().toString().substring(0, 8), "Shift Close Client");

            // One issued invoice of 100000 for this shift → expectedTotal = 100000.
            var doc = new SalesDocumentEntity();
            doc.setType(SalesDocumentType.INVOICE);
            doc.setStatus(SalesDocumentStatus.ISSUED);
            doc.setDocumentNumber("SCJE-" + UUID.randomUUID().toString().substring(0, 8));
            doc.setClientId(clientId);
            doc.setWarehouseId(warehouseId);
            doc.setShiftId(shiftId);
            doc.setCashRegisterId(registerId);
            doc.setTotalNet(new BigDecimal("100000.00"));
            doc.setTotalTax0(BigDecimal.ZERO);
            doc.setTotalTax5(BigDecimal.ZERO);
            doc.setTotalTax19(BigDecimal.ZERO);
            doc.setTotalAmount(new BigDecimal("100000.00"));
            doc.setCreditSale(false);
            em.persist(doc);
            em.flush();
        });
    }

    @Test
    void closingShiftWithShortagePostsDebitFaltanteCreditCash() {
        // expected 100000, counted 90000 → difference +10000 (faltante).
        var cashCount = new CashCountRequest(
                new BigDecimal("90000"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                "Faltante WU36d");

        var response = closeShiftUseCase.execute(shiftId, cashCount);

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            var faltanteId = pucRepo.findByCode("529520").orElseThrow().id();
            var cashId = pucRepo.findByCode("1105").orElseThrow().id();

            var entries = entriesFor(response.id());
            assertThat(entries).hasSize(1);
            var entry = entries.get(0);

            assertThat(entry.getSourceType()).isEqualTo("SHIFT_CLOSE");
            assertThat(entry.getSourceId()).isEqualTo(response.id());

            var persisted = shiftRepository.findById(response.id()).orElseThrow();
            // closing_time is TIMESTAMPTZ and Hibernate reads OffsetDateTime back in UTC, while the
            // entry is dated on the business-local date; convert the persisted instant before comparing.
            var expectedDate = persisted.closingTime().atZoneSameInstant(ZoneId.systemDefault()).toLocalDate();
            assertThat(entry.getEntryDate()).isEqualTo(expectedDate);

            assertThat(totalDebit(entry)).isEqualByComparingTo(totalCredit(entry));
            assertThat(totalDebit(entry)).isEqualByComparingTo("10000");

            assertThat(lineOn(entry, faltanteId).getDebit()).isEqualByComparingTo("10000");
            assertThat(lineOn(entry, faltanteId).getCredit()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(lineOn(entry, cashId).getCredit()).isEqualByComparingTo("10000");
            assertThat(lineOn(entry, cashId).getDebit()).isEqualByComparingTo(BigDecimal.ZERO);
        });
    }

    @Test
    void closingShiftWithSurplusPostsDebitCashCreditSobrante() {
        // expected 100000, counted 110000 → difference -10000 (sobrante).
        var cashCount = new CashCountRequest(
                new BigDecimal("110000"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                "Sobrante WU36d");

        var response = closeShiftUseCase.execute(shiftId, cashCount);

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            var sobranteId = pucRepo.findByCode("429515").orElseThrow().id();
            var cashId = pucRepo.findByCode("1105").orElseThrow().id();

            var entries = entriesFor(response.id());
            assertThat(entries).hasSize(1);
            var entry = entries.get(0);

            assertThat(entry.getSourceType()).isEqualTo("SHIFT_CLOSE");
            assertThat(totalDebit(entry)).isEqualByComparingTo(totalCredit(entry));
            assertThat(totalDebit(entry)).isEqualByComparingTo("10000");

            assertThat(lineOn(entry, cashId).getDebit()).isEqualByComparingTo("10000");
            assertThat(lineOn(entry, cashId).getCredit()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(lineOn(entry, sobranteId).getCredit()).isEqualByComparingTo("10000");
            assertThat(lineOn(entry, sobranteId).getDebit()).isEqualByComparingTo(BigDecimal.ZERO);
        });
    }

    @Test
    void closingShiftWithExactMatchPostsNoEntry() {
        // expected 100000, counted 100000 → difference 0.
        var cashCount = new CashCountRequest(
                new BigDecimal("100000"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                "Cuadre perfecto WU36d");

        var response = closeShiftUseCase.execute(shiftId, cashCount);

        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                assertThat(entriesFor(response.id())).isEmpty());
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private List<JournalEntryEntity> entriesFor(UUID sourceId) {
        return em.createQuery(
                        "select e from JournalEntryEntity e "
                                + "where e.sourceType = :sourceType and e.sourceId = :sourceId",
                        JournalEntryEntity.class)
                .setParameter("sourceType", "SHIFT_CLOSE")
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
