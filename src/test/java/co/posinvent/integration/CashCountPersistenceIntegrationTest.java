package co.posinvent.integration;

import co.posinvent.application.dto.CashCountLineRequest;
import co.posinvent.application.dto.RegisterCashCountRequest;
import co.posinvent.application.usecase.RegisterCashCountUseCase;
import co.posinvent.domain.model.CashCount;
import co.posinvent.domain.repository.CashCountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Audit A4 end-to-end: a cash count registered through the use case must survive the
 * persistence round-trip, so the per-denomination arqueo becomes a historical record
 * instead of being lost after the screen is closed.
 *
 * <p>Deliberately NOT {@code @Transactional}: the shift is seeded in its own committed
 * transaction so the use case observes it like a production caller, and the persisted
 * rows are read back from a fresh transaction.</p>
 */
class CashCountPersistenceIntegrationTest extends AbstractIntegrationTest {

    @Autowired private RegisterCashCountUseCase registerCashCountUseCase;
    @Autowired private CashCountRepository cashCountRepository;

    private UUID shiftId;

    @BeforeEach
    void setUp() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            var registerId = (UUID) em.createNativeQuery(
                            "INSERT INTO cash_registers (name) VALUES ('CASH-COUNT-TEST') RETURNING id")
                    .getSingleResult();
            shiftId = (UUID) em.createNativeQuery("""
                            INSERT INTO shifts (cash_register_id, user_id, opening_amount, status)
                            VALUES (CAST(:reg AS uuid), CAST(:user AS uuid), 0, 'OPEN')
                            RETURNING id
                            """)
                    .setParameter("reg", registerId.toString())
                    .setParameter("user", TEST_USER_ID.toString())
                    .getSingleResult();
        });
    }

    @Test
    void persistsCashCountWithDenominationLinesAndReadsItBackByShift() {
        var request = new RegisterCashCountRequest(
                List.of(
                        new CashCountLineRequest(new BigDecimal("50000.00"), "BILLETE", 2),
                        new CashCountLineRequest(new BigDecimal("100.00"), "MONEDA", 5)),
                "Arqueo de integración");

        var response = registerCashCountUseCase.execute(shiftId, request, TEST_USER_ID);

        assertThat(response.id()).isNotNull();
        assertThat(response.countedTotal()).isEqualByComparingTo("100500.00");
        assertThat(response.expectedTotal()).isEqualByComparingTo("0");
        assertThat(response.difference()).isEqualByComparingTo("100500.00");
        assertThat(response.createdAt()).isNotNull();

        var history = cashCountRepository.findByShiftId(shiftId);

        assertThat(history).hasSize(1);
        var persisted = history.get(0);
        assertThat(persisted.id()).isEqualTo(response.id());
        assertThat(persisted.shiftId()).isEqualTo(shiftId);
        assertThat(persisted.countedTotal()).isEqualByComparingTo("100500.00");
        assertThat(persisted.expectedTotal()).isEqualByComparingTo("0");
        assertThat(persisted.difference()).isEqualByComparingTo("100500.00");

        assertThat(persisted.lines()).hasSize(2);
        assertThat(persisted.lines()).extracting("denominationType")
                .containsExactlyInAnyOrder("BILLETE", "MONEDA");
        assertThat(persisted.lines()).extracting(CashCount.Line::subtotal)
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactlyInAnyOrder(new BigDecimal("100000.00"), new BigDecimal("500.00"));
    }
}
