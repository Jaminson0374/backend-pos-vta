package co.posinvent.integration;

import co.posinvent.application.usecase.CashCountRequest;
import co.posinvent.application.usecase.CloseShiftUseCase;
import co.posinvent.domain.repository.ShiftRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Audit A3 end-to-end: closing a shift generates a ~1 KB Z-Report, which the old
 * {@code shifts.z_report_url VARCHAR(500)} could not hold. After the V117 migration
 * (column type TEXT) the full report must survive the persistence round-trip.
 *
 * <p>Deliberately NOT {@code @Transactional}: the shift is seeded in its own committed
 * transaction so the close use case observes it like a production caller, and the
 * persisted row is read back from a fresh transaction.</p>
 */
class ShiftCloseIntegrationTest extends AbstractIntegrationTest {

    @Autowired private CloseShiftUseCase closeShiftUseCase;
    @Autowired private ShiftRepository shiftRepository;

    private UUID shiftId;

    @BeforeEach
    void setUp() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            var registerId = (UUID) em.createNativeQuery(
                            "INSERT INTO cash_registers (name) VALUES ('SHIFT-CLOSE-TEST') RETURNING id")
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
    void closesShiftAndPersistsFullZReportBeyondLegacy500Chars() {
        var cashCount = new CashCountRequest(
                new BigDecimal("1250000"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                "Cierre de prueba con observaciones de auditoría");

        var response = closeShiftUseCase.execute(shiftId, cashCount);

        assertThat(response.zReportUrl()).isNotNull();
        assertThat(response.zReportUrl().length()).isGreaterThan(500);

        var persisted = shiftRepository.findById(shiftId).orElseThrow();
        assertThat(persisted.zReportUrl()).isNotNull();
        assertThat(persisted.zReportUrl().length()).isGreaterThan(500);
        assertThat(persisted.zReportUrl()).isEqualTo(response.zReportUrl());
    }
}
