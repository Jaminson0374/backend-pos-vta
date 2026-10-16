package co.posinvent.integration;

import co.posinvent.application.dto.AdjustmentRequest;
import co.posinvent.application.dto.DisposalRequest;
import co.posinvent.application.dto.ProduceRequest;
import co.posinvent.application.usecase.CreateAdjustmentUseCase;
import co.posinvent.application.usecase.CreateDisposalUseCase;
import co.posinvent.application.usecase.FormulaProductionUseCase;
import co.posinvent.application.usecase.RecordMovementUseCase;
import co.posinvent.domain.model.Batch;
import co.posinvent.domain.model.MovementType;
import co.posinvent.domain.model.Warehouse;
import co.posinvent.domain.repository.PucAccountRepository;
import co.posinvent.domain.repository.ThirdPartyRepository;
import co.posinvent.infrastructure.adapters.out.persistence.JournalEntryEntity;
import co.posinvent.infrastructure.adapters.out.persistence.JournalEntryLineEntity;
import co.posinvent.infrastructure.adapters.out.persistence.TestDataFactory;
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
 * Integration guard for the flows that did not post to accounting (Contabilidad Fase 2, WU-36c):
 * inventory adjustment, disposal/waste and formula production.
 *
 * <p>Every flow must leave exactly one balanced journal entry, with positive magnitudes, on the
 * PUC accounts declared by the accounting design: adjustment and disposal go against inventario
 * {@code 1435} and pérdida {@code 5195}; production crosses inventario {@code 1435} (finished
 * goods vs consumed components) and sends any cost difference to the production variance account.</p>
 *
 * <p>Deliberately NOT {@code @Transactional}: fixtures are committed through a
 * {@link TransactionTemplate} so the use cases commit their own transactions and the
 * {@code AFTER_COMMIT} accounting listener runs exactly like in production.</p>
 */
class AccountingInventoryProductionIntegrationTest extends AbstractIntegrationTest {

    @Autowired private CreateAdjustmentUseCase createAdjustmentUseCase;
    @Autowired private CreateDisposalUseCase createDisposalUseCase;
    @Autowired private FormulaProductionUseCase formulaProductionUseCase;
    @Autowired private RecordMovementUseCase recordMovementUseCase;
    @Autowired private PucAccountRepository pucRepo;
    @Autowired private ThirdPartyRepository thirdPartyRepo;

    private UUID warehouseId;
    private UUID adjustmentProductId;
    private UUID adjustmentBatchId;
    private UUID disposalProductId;
    private UUID disposalBatchId;
    private UUID rawMaterialId;
    private UUID rawBatchId;
    private UUID finishedProductId;

    @BeforeEach
    void setUp() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            warehouseId = TestDataFactory.createWarehouse(em, "WU36C", Warehouse.WarehouseType.CORTES);
            var systemSupplierId = thirdPartyRepo.findByNumIdentification("000000000-0").orElseThrow().id();

            // ── adjustment fixture: stock 10 @ 5 ──
            adjustmentProductId = TestDataFactory.createProduct(em, "ADJ36C", "Ajuste WU36c", false, true);
            adjustmentBatchId = TestDataFactory.createBatch(em, adjustmentProductId, systemSupplierId,
                    warehouseId, new BigDecimal("10"), Batch.BatchStatus.OPEN);
            TestDataFactory.createStock(em, adjustmentProductId, adjustmentBatchId, warehouseId,
                    new BigDecimal("10"), new BigDecimal("5"));

            // ── disposal fixture: stock 10 @ 5 ──
            disposalProductId = TestDataFactory.createProduct(em, "DEC36C", "Decomiso WU36c", false, true);
            disposalBatchId = TestDataFactory.createBatch(em, disposalProductId, systemSupplierId,
                    warehouseId, new BigDecimal("10"), Batch.BatchStatus.OPEN);
            TestDataFactory.createStock(em, disposalProductId, disposalBatchId, warehouseId,
                    new BigDecimal("10"), new BigDecimal("5"));

            // ── production fixture: raw material stock 50 @ 10, formula ratio 2 ──
            rawMaterialId = TestDataFactory.createProduct(em, "MP36C", "Materia prima WU36c", false, true);
            rawBatchId = TestDataFactory.createBatch(em, rawMaterialId, systemSupplierId,
                    warehouseId, new BigDecimal("50"), Batch.BatchStatus.OPEN);
            TestDataFactory.createStock(em, rawMaterialId, rawBatchId, warehouseId,
                    new BigDecimal("50"), new BigDecimal("10"));
            finishedProductId = TestDataFactory.createProduct(em, "FIN36C", "Producto terminado WU36c", true, true);
            TestDataFactory.createFormula(em, finishedProductId, rawMaterialId, new BigDecimal("2.0"));
            em.flush();
        });

        // A Kardex ENTRY movement so the weighted-average cost of the raw material resolves to 10.
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                recordMovementUseCase.record(
                        rawMaterialId, rawBatchId, warehouseId, MovementType.ENTRY,
                        new BigDecimal("50"), new BigDecimal("10"),
                        BigDecimal.ZERO, new BigDecimal("50"),
                        "TEST_SEED", rawBatchId, "Seed raw material cost"));
    }

    @Test
    void adjustmentPostsBalancedLossAndInventoryEntry() {
        // Delta = 7 - 10 = -3 → loss (5195) debit 3 / inventory (1435) credit 3.
        var response = createAdjustmentUseCase.execute(new AdjustmentRequest(
                adjustmentProductId, adjustmentBatchId, warehouseId,
                "PHYSICAL_COUNT", new BigDecimal("7"), "Conteo físico WU36c"));

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            var inventoryId = pucRepo.findByCode("1435").orElseThrow().id();
            var lossId = pucRepo.findByCode("5195").orElseThrow().id();

            var entries = entriesFor("INVENTORY", response.id());
            assertThat(entries).hasSize(1);
            var entry = entries.get(0);

            assertThat(totalDebit(entry)).isEqualByComparingTo(totalCredit(entry));
            assertThat(totalDebit(entry)).isEqualByComparingTo("3");
            assertThat(totalDebit(entry)).isPositive();

            assertThat(lineOn(entry, lossId).getDebit()).isEqualByComparingTo("3");
            assertThat(lineOn(entry, lossId).getCredit()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(lineOn(entry, inventoryId).getCredit()).isEqualByComparingTo("3");
            assertThat(lineOn(entry, inventoryId).getDebit()).isEqualByComparingTo(BigDecimal.ZERO);
        });
    }

    @Test
    void adjustmentSurplusPostsInventoryDebitAndLossCredit() {
        // Delta = 13 - 10 = +3 → inventory (1435) debit 3 / loss (5195) credit 3.
        var response = createAdjustmentUseCase.execute(new AdjustmentRequest(
                adjustmentProductId, adjustmentBatchId, warehouseId,
                "PHYSICAL_COUNT", new BigDecimal("13"), "Sobrante WU36c"));

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            var inventoryId = pucRepo.findByCode("1435").orElseThrow().id();
            var lossId = pucRepo.findByCode("5195").orElseThrow().id();

            var entries = entriesFor("INVENTORY", response.id());
            assertThat(entries).hasSize(1);
            var entry = entries.get(0);

            assertThat(totalDebit(entry)).isEqualByComparingTo(totalCredit(entry));
            assertThat(lineOn(entry, inventoryId).getDebit()).isEqualByComparingTo("3");
            assertThat(lineOn(entry, lossId).getCredit()).isEqualByComparingTo("3");
        });
    }

    @Test
    void disposalPostsBalancedLossAndInventoryEntryAtStockCost() {
        // 4 units × 5 (stock cost) = 20 → loss (5195) debit 20 / inventory (1435) credit 20.
        var response = createDisposalUseCase.execute(new DisposalRequest(
                disposalProductId, disposalBatchId, warehouseId, "DECOMISO_SANITARIO",
                new BigDecimal("4"), "Decomiso WU36c", "ACTA-WU36C", LocalDate.now()), TEST_USER_ID);

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            var inventoryId = pucRepo.findByCode("1435").orElseThrow().id();
            var lossId = pucRepo.findByCode("5195").orElseThrow().id();

            var entries = entriesFor("INVENTORY", response.id());
            assertThat(entries).hasSize(1);
            var entry = entries.get(0);

            assertThat(totalDebit(entry)).isEqualByComparingTo(totalCredit(entry));
            assertThat(totalDebit(entry)).isEqualByComparingTo("20");

            assertThat(lineOn(entry, lossId).getDebit()).isEqualByComparingTo("20");
            assertThat(lineOn(entry, lossId).getCredit()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(lineOn(entry, inventoryId).getCredit()).isEqualByComparingTo("20");
            assertThat(lineOn(entry, inventoryId).getDebit()).isEqualByComparingTo(BigDecimal.ZERO);
        });
    }

    @Test
    void productionPostsBalancedFinishedGoodsAndComponentConsumptionEntry() {
        // 10 units × ratio 2 = 20 raw units @ 10 → component cost 200; labor 50 → finished good 250.
        var response = formulaProductionUseCase.produce(new ProduceRequest(
                finishedProductId, warehouseId, new BigDecimal("10"),
                new BigDecimal("50"), BigDecimal.ZERO, "Producción WU36c", null),
                TEST_USER_ID);

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            var inventoryId = pucRepo.findByCode("1435").orElseThrow().id();
            var varianceId = pucRepo.findByCode("710595").orElseThrow().id();

            var entries = entriesFor("PRODUCTION", response.productionBatchId());
            assertThat(entries).hasSize(1);
            var entry = entries.get(0);

            assertThat(totalDebit(entry)).isEqualByComparingTo(totalCredit(entry));
            assertThat(totalDebit(entry)).isEqualByComparingTo("250");

            // Finished goods enter inventory (debit 1435 250); components leave (credit 1435 200).
            assertThat(totalDebitOn(entry, inventoryId)).isEqualByComparingTo("250");
            assertThat(totalCreditOn(entry, inventoryId)).isEqualByComparingTo("200");
            // The labor premium (250 - 200) goes to the production variance/cost account.
            assertThat(totalCreditOn(entry, varianceId)).isEqualByComparingTo("50");
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

    private static BigDecimal totalDebitOn(JournalEntryEntity entry, UUID accountId) {
        return entry.getLines().stream()
                .filter(l -> accountId.equals(l.getAccountId()))
                .map(JournalEntryLineEntity::getDebit)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal totalCreditOn(JournalEntryEntity entry, UUID accountId) {
        return entry.getLines().stream()
                .filter(l -> accountId.equals(l.getAccountId()))
                .map(JournalEntryLineEntity::getCredit)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
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
