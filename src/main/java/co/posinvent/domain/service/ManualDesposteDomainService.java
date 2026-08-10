package co.posinvent.domain.service;

import co.posinvent.domain.exception.BusinessException;
import co.posinvent.domain.exception.MassBalanceException;
import co.posinvent.domain.model.Batch;
import co.posinvent.domain.model.Batch.BatchStatus;
import co.posinvent.domain.model.BatchType;
import co.posinvent.domain.model.ManualDespostePlan;
import co.posinvent.domain.model.ManualDespostePlan.Command;
import co.posinvent.domain.model.ManualDespostePlan.DesposteMassBalance;
import co.posinvent.domain.model.ManualDespostePlan.DesposteSourceType;
import co.posinvent.domain.model.ManualDespostePlan.ManualDesposteCutCommand;
import co.posinvent.domain.model.ManualDespostePlan.ManualDesposteCutResult;
import co.posinvent.domain.model.ManualDespostePlan.SourceBatchAction;
import co.posinvent.domain.model.ManualDespostePlan.SourceBatchTransition;
import co.posinvent.domain.model.ManualDespostePlan.StockUpsertDraft;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;

@Service
public final class ManualDesposteDomainService {

    private static final BigDecimal MASS_TOLERANCE_FACTOR = new BigDecimal("0.005");
    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final int SCALE = 6;

    public ManualDespostePlan planForExistingBatch(Batch batch, Command command) {
        validateProcessableBatch(batch, command.sourceBatchId());
        validateManualCommand(command);

        var massBalance = validateMassBalance(
                batch.initialWeight(),
                command.cuts(),
                command.wasteWeight(),
                command.shrinkWeight());
        var costing = calculateYieldCosting(batch.purchaseCost(), command.cuts());

        var childBatchPlans = costing.cuts().stream()
                .map(cut -> new ManualDespostePlan.ChildBatchPlan(
                        cut.productId(),
                        cut.warehouseId(),
                        batch.supplierId(),
                        batch.entryDate(),
                        cut.weight(),
                        cut.allocatedCost(),
                        cut.expirationDate() != null ? cut.expirationDate() : batch.expirationDate(),
                        "Desposte — " + (command.notes() != null ? command.notes() : ""),
                        BatchType.CHILD
                ))
                .toList();

        return new ManualDespostePlan(
                batch.id(),
                massBalance,
                costing.totalCommercialValue(),
                costing.totalAllocatedCost(),
                costing.cuts(),
                buildStockUpserts(batch.id(), costing.cuts()),
                childBatchPlans,
                new SourceBatchTransition(
                        batch.id(),
                        batch.status(),
                        BatchStatus.CLOSED,
                        SourceBatchAction.CLOSE)
        );
    }

    public DesposteMassBalance validateMassBalance(
            BigDecimal inputWeight,
            List<ManualDesposteCutCommand> cuts,
            BigDecimal wasteWeight,
            BigDecimal shrinkWeight
    ) {
        var normalizedInputWeight = requirePositive(inputWeight, "El peso inicial del lote debe ser mayor a cero");
        var normalizedWaste = requireNonNegative(wasteWeight, "La merma operativa no puede ser negativa");
        var normalizedShrink = requireNonNegative(shrinkWeight, "La merma tecnica no puede ser negativa");

        var totalCutsWeight = cuts.stream()
                .map(ManualDesposteCutCommand::weight)
                .map(weight -> requirePositive(weight, "Cada corte debe tener peso mayor a cero"))
                .reduce(ZERO, BigDecimal::add)
                .setScale(SCALE, RoundingMode.HALF_UP);

        var deviation = normalizedInputWeight
                .subtract(totalCutsWeight.add(normalizedWaste).add(normalizedShrink))
                .abs()
                .setScale(SCALE, RoundingMode.HALF_UP);
        var tolerance = normalizedInputWeight
                .multiply(MASS_TOLERANCE_FACTOR)
                .setScale(SCALE, RoundingMode.HALF_UP);
        var withinTolerance = deviation.compareTo(tolerance) <= 0;

        var result = new DesposteMassBalance(
                normalizedInputWeight,
                totalCutsWeight,
                normalizedWaste,
                normalizedShrink,
                deviation,
                tolerance,
                withinTolerance
        );

        if (!withinTolerance) {
            throw new MassBalanceException(
                    "MVM excedida: desviacion " + format(deviation)
                            + " kg sobre tolerancia " + format(tolerance) + " kg"
            );
        }

        return result;
    }

    public YieldCostingResult calculateYieldCosting(
            BigDecimal purchaseCost,
            List<ManualDesposteCutCommand> cuts
    ) {
        var normalizedPurchaseCost = requirePositive(purchaseCost, "El costo del lote debe ser mayor a cero");
        if (cuts.isEmpty()) {
            throw new BusinessException("EMPTY_DESPOSTE_CUTS", "El desposte manual requiere al menos un corte");
        }

        var validatedCuts = new ArrayList<ManualDesposteCutCommand>(cuts.size());
        for (int index = 0; index < cuts.size(); index++) {
            validatedCuts.add(validateCut(cuts.get(index), index));
        }

        var totalCommercialValue = validatedCuts.stream()
                .map(cut -> cut.weight().multiply(cut.suggestedSalePrice()).setScale(SCALE, RoundingMode.HALF_UP))
                .reduce(ZERO, BigDecimal::add)
                .setScale(SCALE, RoundingMode.HALF_UP);

        if (totalCommercialValue.compareTo(ZERO) <= 0) {
            throw new BusinessException(
                    "INVALID_TOTAL_COMMERCIAL_VALUE",
                    "El valor comercial total debe ser mayor a cero"
            );
        }

        var resolvedCuts = new ArrayList<ManualDesposteCutResult>(validatedCuts.size());
        var allocatedCostAccumulator = ZERO.setScale(SCALE, RoundingMode.HALF_UP);

        for (int index = 0; index < validatedCuts.size(); index++) {
            var cut = validatedCuts.get(index);
            var commercialValue = cut.weight()
                    .multiply(cut.suggestedSalePrice())
                    .setScale(SCALE, RoundingMode.HALF_UP);
            var isLastCut = index == validatedCuts.size() - 1;
            var allocatedCost = isLastCut
                    ? normalizedPurchaseCost.subtract(allocatedCostAccumulator).setScale(SCALE, RoundingMode.HALF_UP)
                    : normalizedPurchaseCost.multiply(commercialValue)
                            .divide(totalCommercialValue, SCALE, RoundingMode.HALF_UP);

            allocatedCostAccumulator = allocatedCostAccumulator.add(allocatedCost).setScale(SCALE, RoundingMode.HALF_UP);

            resolvedCuts.add(new ManualDesposteCutResult(
                    cut.productId(),
                    cut.warehouseId(),
                    cut.weight(),
                    cut.suggestedSalePrice(),
                    commercialValue,
                    allocatedCost,
                    allocatedCost.divide(cut.weight(), SCALE, RoundingMode.HALF_UP),
                    cut.expirationDate()
            ));
        }

        var totalAllocatedCost = resolvedCuts.stream()
                .map(ManualDesposteCutResult::allocatedCost)
                .reduce(ZERO, BigDecimal::add)
                .setScale(SCALE, RoundingMode.HALF_UP);

        if (totalAllocatedCost.compareTo(normalizedPurchaseCost) != 0) {
            throw new BusinessException(
                    "YIELD_COSTING_INCONSISTENT",
                    "Yield Costing inconsistente: el costo distribuido no cierra"
            );
        }

        return new YieldCostingResult(totalCommercialValue, totalAllocatedCost, resolvedCuts);
    }

    private void validateProcessableBatch(Batch batch, java.util.UUID sourceBatchId) {
        if (!Objects.equals(batch.id(), sourceBatchId)) {
            throw new BusinessException(
                    "SOURCE_BATCH_MISMATCH",
                    "El lote origen del request no coincide con el lote recibido"
            );
        }

        if (batch.status() == BatchStatus.CLOSED) {
            throw new BusinessException(
                    "BATCH_IMMUTABLE",
                    "Un lote cerrado no puede volver a despostarse"
            );
        }
    }

    private void validateManualCommand(Command command) {
        if (command.sourceType() != DesposteSourceType.MANUAL) {
            throw new BusinessException(
                    "UNSUPPORTED_DESPOSTE_SOURCE_TYPE",
                    "Este slice inicial solo admite desposte manual"
            );
        }

        if (command.manualJustification() == null || command.manualJustification().trim().isEmpty()) {
            throw new BusinessException(
                    "MANUAL_JUSTIFICATION_REQUIRED",
                    "La justificacion manual es obligatoria"
            );
        }

        requireNonNegative(command.wasteWeight(), "La merma operativa no puede ser negativa");
        requireNonNegative(command.shrinkWeight(), "La merma tecnica no puede ser negativa");

        if (command.cuts().isEmpty()) {
            throw new BusinessException(
                    "EMPTY_DESPOSTE_CUTS",
                    "Debe registrar al menos un corte resultante"
            );
        }
    }

    private ManualDesposteCutCommand validateCut(ManualDesposteCutCommand cut, int index) {
        if (cut.productId() == null) {
            throw new BusinessException("INVALID_DESPOSTE_CUT", "El corte " + (index + 1) + " requiere productId");
        }

        if (cut.warehouseId() == null) {
            throw new BusinessException("INVALID_DESPOSTE_CUT", "El corte " + (index + 1) + " requiere warehouseId");
        }

        return new ManualDesposteCutCommand(
                cut.productId(),
                cut.warehouseId(),
                requirePositive(cut.weight(), "El corte " + (index + 1) + " debe tener peso mayor a cero"),
                requirePositive(
                        cut.suggestedSalePrice(),
                        "El corte " + (index + 1) + " debe tener precio sugerido mayor a cero"
                ),
                cut.expirationDate()
        );
    }

    private List<StockUpsertDraft> buildStockUpserts(
            java.util.UUID batchId,
            List<ManualDesposteCutResult> cuts
    ) {
        var drafts = new LinkedHashMap<StockKey, StockAccumulator>();

        for (var cut : cuts) {
            var key = new StockKey(cut.productId(), cut.warehouseId());
            drafts.computeIfAbsent(key, ignored -> new StockAccumulator())
                    .add(cut.weight(), cut.allocatedCost());
        }

        return drafts.entrySet().stream()
                .map(entry -> new StockUpsertDraft(
                        entry.getKey().productId(),
                        batchId,
                        entry.getKey().warehouseId(),
                        entry.getValue().quantity().setScale(SCALE, RoundingMode.HALF_UP),
                        entry.getValue().allocatedCost()
                                .divide(entry.getValue().quantity(), SCALE, RoundingMode.HALF_UP)
                ))
                .toList();
    }

    private BigDecimal requirePositive(BigDecimal value, String message) {
        if (value == null || value.compareTo(ZERO) <= 0) {
            throw new BusinessException("INVALID_DESPOSTE_VALUE", message);
        }
        return value.setScale(SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal requireNonNegative(BigDecimal value, String message) {
        if (value == null || value.compareTo(ZERO) < 0) {
            throw new BusinessException("INVALID_DESPOSTE_VALUE", message);
        }
        return value.setScale(SCALE, RoundingMode.HALF_UP);
    }

    private String format(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    public record YieldCostingResult(
            BigDecimal totalCommercialValue,
            BigDecimal totalAllocatedCost,
            List<ManualDesposteCutResult> cuts
    ) {
        public YieldCostingResult {
            cuts = List.copyOf(cuts);
        }
    }

    private record StockKey(java.util.UUID productId, java.util.UUID warehouseId) { }

    private static final class StockAccumulator {
        private BigDecimal quantity = ZERO.setScale(SCALE, RoundingMode.HALF_UP);
        private BigDecimal allocatedCost = ZERO.setScale(SCALE, RoundingMode.HALF_UP);

        void add(BigDecimal quantity, BigDecimal allocatedCost) {
            this.quantity = this.quantity.add(quantity).setScale(SCALE, RoundingMode.HALF_UP);
            this.allocatedCost = this.allocatedCost.add(allocatedCost).setScale(SCALE, RoundingMode.HALF_UP);
        }

        BigDecimal quantity() {
            return quantity;
        }

        BigDecimal allocatedCost() {
            return allocatedCost;
        }
    }
}
