package co.posinvent.domain.service;

import co.posinvent.domain.exception.BusinessException;
import co.posinvent.domain.exception.MassBalanceException;
import co.posinvent.domain.model.Batch;
import co.posinvent.domain.model.ManualDespostePlan;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ManualDesposteDomainServiceTest {

    private ManualDesposteDomainService service;

    @BeforeEach
    void setUp() {
        service = new ManualDesposteDomainService();
    }

    @Test
    void validateMassBalance_acceptsDeviationAtExactTolerance() {
        var result = service.validateMassBalance(
                new BigDecimal("100"),
                List.of(
                        cut("60", "20"),
                        cut("35", "10")
                ),
                new BigDecimal("4"),
                new BigDecimal("0.5")
        );

        assertThat(result.withinTolerance()).isTrue();
        assertThat(result.deviation()).isEqualByComparingTo("0.500000");
        assertThat(result.tolerance()).isEqualByComparingTo("0.500000");
    }

    @Test
    void validateMassBalance_rejectsDeviationOverTolerance() {
        assertThatThrownBy(() -> service.validateMassBalance(
                new BigDecimal("100"),
                List.of(
                        cut("60", "20"),
                        cut("35", "10")
                ),
                new BigDecimal("4"),
                BigDecimal.ZERO
        ))
                .isInstanceOf(MassBalanceException.class)
                .hasMessage("MVM excedida: desviacion 1 kg sobre tolerancia 0.5 kg");
    }

    @Test
    void planForExistingBatch_distributesCostCreatesStockUpsertsAndClosesBatch() {
        var batch = new Batch(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                LocalDate.of(2026, 5, 13),
                new BigDecimal("100"),
                new BigDecimal("1000"),
                Batch.BatchStatus.OPEN,
                "Lote test",
                null,
                UUID.randomUUID(),
                OffsetDateTime.now().minusDays(1),
                OffsetDateTime.now().minusHours(1),
                null,
                null,
                null, null, null
        );

        var plan = service.planForExistingBatch(batch, new ManualDespostePlan.Command(
                batch.id(),
                ManualDespostePlan.DesposteSourceType.MANUAL,
                "Slice 1 manual sin integracion de bascula",
                new BigDecimal("4"),
                new BigDecimal("0.5"),
                "Primer slice",
                List.of(
                        new ManualDespostePlan.ManualDesposteCutCommand(
                                UUID.randomUUID(),
                                UUID.randomUUID(),
                                new BigDecimal("60"),
                                new BigDecimal("20")
                        ),
                        new ManualDespostePlan.ManualDesposteCutCommand(
                                UUID.randomUUID(),
                                UUID.randomUUID(),
                                new BigDecimal("35"),
                                new BigDecimal("10")
                        )
                )
        ));

        assertThat(plan.totalCommercialValue()).isEqualByComparingTo("1550.000000");
        assertThat(plan.totalAllocatedCost()).isEqualByComparingTo("1000.000000");
        assertThat(plan.cuts()).hasSize(2);
        assertThat(plan.cuts().get(0).unitCost()).isEqualByComparingTo("12.903226");
        assertThat(plan.cuts().get(1).unitCost()).isEqualByComparingTo("6.451613");
        assertThat(plan.stockUpserts()).hasSize(2);
        assertThat(plan.sourceBatchTransition().previousStatus()).isEqualTo(Batch.BatchStatus.OPEN);
        assertThat(plan.sourceBatchTransition().nextStatus()).isEqualTo(Batch.BatchStatus.CLOSED);
        assertThat(plan.sourceBatchTransition().action()).isEqualTo(ManualDespostePlan.SourceBatchAction.CLOSE);
    }

    @Test
    void calculateYieldCosting_requiresAtLeastOneCut() {
        assertThatThrownBy(() -> service.calculateYieldCosting(new BigDecimal("1000"), List.of()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo("EMPTY_DESPOSTE_CUTS");
    }

    private ManualDespostePlan.ManualDesposteCutCommand cut(String weight, String suggestedSalePrice) {
        return new ManualDespostePlan.ManualDesposteCutCommand(
                UUID.randomUUID(),
                UUID.randomUUID(),
                new BigDecimal(weight),
                new BigDecimal(suggestedSalePrice)
        );
    }
}
