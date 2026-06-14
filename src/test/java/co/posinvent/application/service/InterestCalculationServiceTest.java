package co.posinvent.application.service;

import co.posinvent.application.dto.InterestCalculationResponse;
import co.posinvent.domain.model.AccountsReceivable;
import co.posinvent.domain.model.CompanyConfig;
import co.posinvent.domain.repository.AccountsReceivableRepository;
import co.posinvent.domain.repository.CompanyConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InterestCalculationServiceTest {

    @Mock private AccountsReceivableRepository arRepo;
    @Mock private CompanyConfigRepository configRepo;

    private InterestCalculationService service;

    /** Today's date used across tests. */
    private static final LocalDate TODAY = LocalDate.now();

    private static final BigDecimal ONE_MILLION = new BigDecimal("1000000.00");

    @BeforeEach
    void setUp() {
        service = new InterestCalculationService(arRepo, configRepo);
    }

    // ── Scenario 1: AR without own rate uses CompanyConfig fallback ─────────

    @Test
    void arWithoutOwnRate_usesCompanyConfigFallback() {
        // Config: 3% moratory rate, no grace, simple interest
        when(configRepo.findConfig()).thenReturn(Optional.of(config(
                new BigDecimal("3"), 0, "NONE")));

        // AR: interestRate=null, outstanding=1,000,000, lastCalc=null, dueDate=15 days ago
        var ar = ar(
                UUID.randomUUID(), null, ONE_MILLION,
                null, BigDecimal.ZERO,
                TODAY.minusDays(15));

        when(arRepo.findOverdueBeforeGrace(any(), eq(0)))
                .thenReturn(List.of(ar));
        when(arRepo.save(any(AccountsReceivable.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        var result = service.calculateOverdueInterest();

        assertThat(result.processedCount()).isEqualTo(1);
        assertThat(result.skippedCount()).isEqualTo(0);
        assertThat(result.totalInterestCalculated())
                .isEqualByComparingTo("15000.00"); // 1M * 0.03 * 15/30

        var captor = ArgumentCaptor.forClass(AccountsReceivable.class);
        verify(arRepo).save(captor.capture());
        var saved = captor.getValue();
        assertThat(saved.interestAmount()).isEqualByComparingTo("15000.00");
        assertThat(saved.lastInterestCalcDate()).isEqualTo(TODAY);
    }

    // ── Scenario 2: AR with own rate prevails over CompanyConfig ────────────

    @Test
    void arWithOwnRate_prevailsOverCompanyConfig() {
        // Config: 2% moratory rate
        when(configRepo.findConfig()).thenReturn(Optional.of(config(
                new BigDecimal("2"), 0, "NONE")));

        UUID arAId = UUID.randomUUID();
        UUID arBId = UUID.randomUUID();

        // AR-A: no own rate → uses 2% config rate
        var arA = ar(arAId, null, ONE_MILLION,
                null, BigDecimal.ZERO,
                TODAY.minusDays(15));

        // AR-B: own rate 5% → uses own rate
        var arB = ar(arBId, new BigDecimal("5"), ONE_MILLION,
                null, BigDecimal.ZERO,
                TODAY.minusDays(15));

        when(arRepo.findOverdueBeforeGrace(any(), eq(0)))
                .thenReturn(List.of(arA, arB));
        when(arRepo.save(any(AccountsReceivable.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        var result = service.calculateOverdueInterest();

        assertThat(result.processedCount()).isEqualTo(2);
        assertThat(result.skippedCount()).isEqualTo(0);

        var captor = ArgumentCaptor.forClass(AccountsReceivable.class);
        verify(arRepo, times(2)).save(captor.capture());
        var savedARs = captor.getAllValues();

        // AR-A uses 2%: 1M * 0.02 * 15/30 = 10,000
        var savedA = savedARs.stream()
                .filter(a -> a.id().equals(arAId))
                .findFirst().orElseThrow();
        assertThat(savedA.interestAmount()).isEqualByComparingTo("10000.00");

        // AR-B uses 5%: 1M * 0.05 * 15/30 = 25,000
        var savedB = savedARs.stream()
                .filter(a -> a.id().equals(arBId))
                .findFirst().orElseThrow();
        assertThat(savedB.interestAmount()).isEqualByComparingTo("25000.00");
    }

    // ── Scenario 3: Same-day guard skips ────────────────────────────────────

    @Test
    void sameDayGuard_skipsArAlreadyCalculatedToday() {
        when(configRepo.findConfig()).thenReturn(Optional.of(config(
                new BigDecimal("3"), 0, "NONE")));

        // AR with lastInterestCalcDate = today
        var ar = new AccountsReceivable(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                ONE_MILLION, BigDecimal.ZERO, ONE_MILLION,
                TODAY.minusDays(30), AccountsReceivable.ArStatus.OVERDUE,
                null, null,
                null, new BigDecimal("5000.00"),
                TODAY); // ← same day!

        when(arRepo.findOverdueBeforeGrace(any(), eq(0)))
                .thenReturn(List.of(ar));

        var result = service.calculateOverdueInterest();

        assertThat(result.processedCount()).isEqualTo(0);
        assertThat(result.skippedCount()).isEqualTo(1);
        assertThat(result.totalInterestCalculated()).isEqualByComparingTo("0.00");

        // AR was NOT saved (no change to interestAmount)
        verify(arRepo, never()).save(any(AccountsReceivable.class));
    }

    // ── Scenario 4: Grace days respected (plumbing verified) ────────────────

    @Test
    void graceDays_flowsFromConfigToRepositoryCall() {
        when(configRepo.findConfig()).thenReturn(Optional.of(config(
                new BigDecimal("3"), 15, "NONE")));

        // AR past due date, past grace
        var ar = ar(UUID.randomUUID(), null, ONE_MILLION,
                null, BigDecimal.ZERO,
                TODAY.minusDays(20));

        when(arRepo.findOverdueBeforeGrace(any(), eq(15)))
                .thenReturn(List.of(ar));
        when(arRepo.save(any(AccountsReceivable.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        var result = service.calculateOverdueInterest();

        // Grace days 15 flows correctly to repo call; AR past 20 days IS processed
        assertThat(result.processedCount()).isEqualTo(1);
        assertThat(result.skippedCount()).isEqualTo(0);

        verify(arRepo).findOverdueBeforeGrace(any(), eq(15));
    }

    // ── Scenario 5: Batch processes all eligible ARs ────────────────────────

    @Test
    void batch_processesAllEligibleArsAndSkipsSameDay() {
        when(configRepo.findConfig()).thenReturn(Optional.of(config(
                new BigDecimal("3"), 0, "NONE")));

        // 3 eligible ARs
        var ar1 = ar(UUID.randomUUID(), null, ONE_MILLION,
                null, BigDecimal.ZERO, TODAY.minusDays(15));
        var ar2 = ar(UUID.randomUUID(), null, ONE_MILLION,
                null, BigDecimal.ZERO, TODAY.minusDays(20));
        var ar3 = ar(UUID.randomUUID(), null, ONE_MILLION,
                null, BigDecimal.ZERO, TODAY.minusDays(30));

        // 1 same-day AR (already calculated today)
        var arSameDay = new AccountsReceivable(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                ONE_MILLION, BigDecimal.ZERO, ONE_MILLION,
                TODAY.minusDays(30), AccountsReceivable.ArStatus.OVERDUE,
                null, null,
                null, new BigDecimal("2000.00"),
                TODAY);

        // findOverdueBeforeGrace returns all 4 (including the same-day one)
        when(arRepo.findOverdueBeforeGrace(any(), eq(0)))
                .thenReturn(List.of(ar1, ar2, ar3, arSameDay));
        when(arRepo.save(any(AccountsReceivable.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        var result = service.calculateOverdueInterest();

        assertThat(result.processedCount()).isEqualTo(3);
        assertThat(result.skippedCount()).isEqualTo(1);
        verify(arRepo, times(3)).save(any(AccountsReceivable.class));
    }

    // ── Scenario 6: Simple interest (compoundFrequency=NONE) ────────────────

    @Test
    void simpleInterest_calculatesCorrectly() {
        // 1,000,000 * 0.03 * (15/30) = 15,000
        BigDecimal interest = InterestCalculationService.computeInterest(
                new BigDecimal("1000000"), new BigDecimal("3"), 15, "NONE");

        assertThat(interest).isEqualByComparingTo("15000.00");
    }

    @Test
    void simpleInterest_largeValues() {
        // 5,000,000 * 0.12 * (90/30) = 5,000,000 * 0.12 * 3 = 1,800,000
        BigDecimal interest = InterestCalculationService.computeInterest(
                new BigDecimal("5000000"), new BigDecimal("12"), 90, "NONE");

        assertThat(interest).isEqualByComparingTo("1800000.00");
    }

    // ── Scenario 7: Compound monthly interest ───────────────────────────────

    @Test
    void compoundMonthly_calculatesCorrectly() {
        // outstanding=1,000,000, rate=3% annual, days=65
        // months = floor(65/30) = 2
        // (1 + 0.03)^2 - 1 = 1.0609 - 1 = 0.0609
        // interest = 1,000,000 * 0.0609 = 60,900
        BigDecimal interest = InterestCalculationService.computeInterest(
                new BigDecimal("1000000"), new BigDecimal("3"), 65, "MONTHLY");

        assertThat(interest).isEqualByComparingTo("60900.00");
    }

    @Test
    void compoundMonthly_singleMonth() {
        // months = floor(30/30) = 1
        // (1 + 0.06)^1 - 1 = 0.06
        // 1,000,000 * 0.06 = 60,000
        BigDecimal interest = InterestCalculationService.computeInterest(
                new BigDecimal("1000000"), new BigDecimal("6"), 30, "MONTHLY");

        assertThat(interest).isEqualByComparingTo("60000.00");
    }

    @Test
    void compoundMonthly_lessThanOneMonth_fallsBackToSimple() {
        // 20 days < 1 month, falls back to simple:
        // 1,000,000 * 0.03 * (20/30) = 1,000,000 * 0.02 = 20,000
        BigDecimal interest = InterestCalculationService.computeInterest(
                new BigDecimal("1000000"), new BigDecimal("3"), 20, "MONTHLY");

        assertThat(interest).isEqualByComparingTo("20000.00");
    }

    // ── Scenario 8: Zero rate → skip ───────────────────────────────────────

    @Test
    void zeroRate_configSkips() {
        // Config with moratoryInterestRate=0
        when(configRepo.findConfig()).thenReturn(Optional.of(config(
                BigDecimal.ZERO, 0, "NONE")));

        // Even if ARs exist, zero rate → early return from service
        var result = service.calculateOverdueInterest();

        assertThat(result.processedCount()).isEqualTo(0);
        assertThat(result.totalInterestCalculated()).isEqualByComparingTo("0.00");
        assertThat(result.errors()).contains("No se ha configurado la tasa de interés moratorio.");

        // Repository was never queried for overdue ARs
        verify(arRepo, never()).findOverdueBeforeGrace(any(), anyInt());
    }

    @Test
    void zeroRate_nullConfigRateSkips() {
        // Config with moratoryInterestRate=null
        when(configRepo.findConfig()).thenReturn(Optional.of(config(
                null, 0, "NONE")));

        var result = service.calculateOverdueInterest();

        assertThat(result.processedCount()).isEqualTo(0);
        assertThat(result.errors()).contains("No se ha configurado la tasa de interés moratorio.");
    }

    // ── Edge case: Empty config → error ─────────────────────────────────────

    @Test
    void emptyConfig_returnsError() {
        when(configRepo.findConfig()).thenReturn(Optional.empty());

        var result = service.calculateOverdueInterest();

        assertThat(result.processedCount()).isEqualTo(0);
        assertThat(result.skippedCount()).isEqualTo(0);
        assertThat(result.errors()).contains("No se encontró la configuración de empresa.");
    }

    // ── Edge case: AR with zero outstanding → skip ──────────────────────────

    @Test
    void zeroOutstanding_skipsAr() {
        when(configRepo.findConfig()).thenReturn(Optional.of(config(
                new BigDecimal("3"), 0, "NONE")));

        var ar = new AccountsReceivable(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                TODAY.minusDays(30), AccountsReceivable.ArStatus.PAID,
                null, null,
                null, BigDecimal.ZERO, null);

        when(arRepo.findOverdueBeforeGrace(any(), eq(0)))
                .thenReturn(List.of(ar));

        var result = service.calculateOverdueInterest();

        assertThat(result.processedCount()).isEqualTo(0);
        assertThat(result.skippedCount()).isEqualTo(1); // computeInterest returns 0 → skipped
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private CompanyConfig config(BigDecimal moratoryRate, int graceDays, String compoundFrequency) {
        return new CompanyConfig(
                1L, "TestCo", "123", null, null, null, null, null, null,
                null, null,
                moratoryRate,
                graceDays,
                compoundFrequency,
                null, null, null, null, null, null,
                false,
                null, null);
    }

    private AccountsReceivable ar(
            UUID id, BigDecimal interestRate, BigDecimal outstanding,
            AccountsReceivable.ArStatus status, BigDecimal existingInterest,
            LocalDate dueDate) {
        return new AccountsReceivable(
                id,
                UUID.randomUUID(), // clientId
                UUID.randomUUID(), // documentId
                outstanding,       // totalAmount (same as outstanding for simplicity)
                BigDecimal.ZERO,   // paidAmount
                outstanding,
                dueDate,
                status != null ? status : AccountsReceivable.ArStatus.OVERDUE,
                null,              // createdAt
                null,              // updatedAt
                interestRate,
                existingInterest != null ? existingInterest : BigDecimal.ZERO,
                null);             // lastInterestCalcDate = null
    }
}
