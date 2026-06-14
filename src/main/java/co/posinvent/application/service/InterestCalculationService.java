package co.posinvent.application.service;

import co.posinvent.application.dto.InterestCalculationResponse;
import co.posinvent.domain.model.AccountsReceivable;
import co.posinvent.domain.repository.AccountsReceivableRepository;
import co.posinvent.domain.repository.CompanyConfigRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Calculates moratory interest on overdue accounts receivable.
 * Runs daily at 2 AM as a scheduled job and can also be triggered manually
 * via the REST API.
 */
@Service
public class InterestCalculationService {

    private static final Logger log = LoggerFactory.getLogger(InterestCalculationService.class);

    /** Scale for intermediate compound interest calculations (10 decimal places). */
    private static final int CALC_SCALE = 10;

    private final AccountsReceivableRepository arRepo;
    private final CompanyConfigRepository configRepo;

    public InterestCalculationService(
            AccountsReceivableRepository arRepo,
            CompanyConfigRepository configRepo
    ) {
        this.arRepo = arRepo;
        this.configRepo = configRepo;
    }

    /**
     * Scheduled job: runs daily at 2:00 AM to calculate interest on all overdue ARs.
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void scheduledCalculateOverdueInterest() {
        log.info("Starting scheduled interest calculation...");
        try {
            var result = calculateOverdueInterest();
            log.info("Scheduled interest calculation complete: processed={}, skipped={}, totalInterest={}",
                    result.processedCount(), result.skippedCount(), result.totalInterestCalculated());
        } catch (Exception e) {
            log.error("Scheduled interest calculation failed", e);
        }
    }

    /**
     * Main public method: calculates interest on all overdue accounts receivable
     * that are past the grace period. Can be called from the scheduler or REST API.
     *
     * @return InterestCalculationResponse with processed count, total interest, skipped count, and any errors
     */
    @Transactional
    public InterestCalculationResponse calculateOverdueInterest() {
        var today = LocalDate.now();
        var errors = new ArrayList<String>();
        var totalInterest = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        int processedCount = 0;
        int skippedCount = 0;

        // 1. Load company config (single row, id=1)
        var configOpt = configRepo.findConfig();
        if (configOpt.isEmpty()) {
            errors.add("No se encontró la configuración de empresa.");
            return new InterestCalculationResponse(0, BigDecimal.ZERO, 0, errors);
        }
        var config = configOpt.get();

        var moratoryRate = config.moratoryInterestRate();
        if (moratoryRate == null || moratoryRate.compareTo(BigDecimal.ZERO) <= 0) {
            errors.add("No se ha configurado la tasa de interés moratorio.");
            return new InterestCalculationResponse(0, BigDecimal.ZERO, 0, errors);
        }

        var graceDays = config.interestGraceDays() != null ? config.interestGraceDays() : 0;
        var compoundFrequency = config.interestCompoundFrequency() != null
                ? config.interestCompoundFrequency().toUpperCase() : "NONE";

        // 2. Query overdue ARs that are past the grace period
        var overdueArs = arRepo.findOverdueBeforeGrace(today, graceDays);

        for (var ar : overdueArs) {
            try {
                // 3a. Same-day guard: skip if interest was already calculated today
                if (ar.lastInterestCalcDate() != null && ar.lastInterestCalcDate().equals(today)) {
                    skippedCount++;
                    continue;
                }

                // 3b. Determine effective interest rate: AR-specific rate overrides config rate
                BigDecimal effectiveRate = ar.interestRate() != null
                        && ar.interestRate().compareTo(BigDecimal.ZERO) > 0
                        ? ar.interestRate()
                        : moratoryRate;

                if (effectiveRate == null || effectiveRate.compareTo(BigDecimal.ZERO) <= 0) {
                    skippedCount++;
                    continue;
                }

                // 3c. Calculate days since last interest calculation (or due date if never calculated)
                var startDate = ar.lastInterestCalcDate() != null ? ar.lastInterestCalcDate() : ar.dueDate();
                long days = ChronoUnit.DAYS.between(startDate, today);
                if (days <= 0) {
                    skippedCount++;
                    continue;
                }

                // 3d. Compute interest based on compound frequency
                BigDecimal calculated = computeInterest(
                        ar.outstanding(), effectiveRate, days, compoundFrequency);

                if (calculated.compareTo(BigDecimal.ZERO) <= 0) {
                    skippedCount++;
                    continue;
                }

                // 3e. Update AR: add interest, set last calculation date
                var newInterestAmount = ar.interestAmount().add(calculated);
                var updated = new AccountsReceivable(
                        ar.id(), ar.clientId(), ar.documentId(),
                        ar.totalAmount(), ar.paidAmount(), ar.outstanding(),
                        ar.dueDate(), ar.status(),
                        ar.createdAt(), null,
                        ar.interestRate(), newInterestAmount, today
                );
                arRepo.save(updated);

                // 3f. Accumulate
                totalInterest = totalInterest.add(calculated);
                processedCount++;
            } catch (Exception e) {
                errors.add("Error procesando AR " + ar.id() + ": " + e.getMessage());
                log.warn("Error processing AR {}: {}", ar.id(), e.getMessage());
            }
        }

        return new InterestCalculationResponse(processedCount, totalInterest, skippedCount, errors);
    }

    /**
     * Backward-compatible alias for {@link #calculateOverdueInterest()}.
     * Referenced by AccountsReceivableController.
     */
    @Transactional
    public InterestCalculationResponse calculateAllOverdueInterest() {
        return calculateOverdueInterest();
    }

    /**
     * Computes interest according to the compound frequency configured.
     *
     * @param outstanding      the outstanding balance
     * @param rate             the annual interest rate as a percentage (e.g., 12.0 for 12%)
     * @param days             number of days overdue (since last calc or due date)
     * @param compoundFrequency "NONE", "MONTHLY", or "DAILY"
     * @return the calculated interest amount, rounded to 2 decimals
     */
    static BigDecimal computeInterest(
            BigDecimal outstanding, BigDecimal rate, long days, String compoundFrequency) {

        if (outstanding == null || outstanding.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        if (rate == null || rate.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        if (days <= 0) {
            return BigDecimal.ZERO;
        }

        return switch (compoundFrequency) {
            case "MONTHLY" -> computeCompoundMonthly(outstanding, rate, days);
            case "DAILY"   -> computeCompoundDaily(outstanding, rate, days);
            default        -> computeSimple(outstanding, rate, days);  // NONE or unknown
        };
    }

    /**
     * Simple interest: outstanding × (rate / 100) × (days / 30.0)
     */
    private static BigDecimal computeSimple(BigDecimal outstanding, BigDecimal rate, long days) {
        // (rate / 100) * (days / 30)
        var rateDecimal = rate.divide(BigDecimal.valueOf(100), CALC_SCALE, RoundingMode.HALF_UP);
        var monthsFraction = BigDecimal.valueOf(days)
                .divide(BigDecimal.valueOf(30), CALC_SCALE, RoundingMode.HALF_UP);
        var factor = rateDecimal.multiply(monthsFraction);
        return outstanding.multiply(factor).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Compound monthly: outstanding × ((1 + rate/100)^months - 1)
     * where months = floor(days / 30)
     */
    private static BigDecimal computeCompoundMonthly(BigDecimal outstanding, BigDecimal rate, long days) {
        long months = days / 30;
        if (months <= 0) {
            // Less than a full month — fall back to simple interest
            return computeSimple(outstanding, rate, days);
        }

        var annualRateDecimal = rate.divide(BigDecimal.valueOf(100), CALC_SCALE, RoundingMode.HALF_UP);
        var base = BigDecimal.ONE.add(annualRateDecimal);
        var compound = base.pow((int) months);
        var factor = compound.subtract(BigDecimal.ONE);
        return outstanding.multiply(factor).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Compound daily: outstanding × ((1 + rate/100)^(days/30) - 1)
     * Uses fractional exponent approximation with Math.pow for precision.
     */
    private static BigDecimal computeCompoundDaily(BigDecimal outstanding, BigDecimal rate, long days) {
        // (1 + rate/100)^(days/30) - 1
        var annualRateDecimal = rate.doubleValue() / 100.0;
        var exponent = (double) days / 30.0;
        var compound = Math.pow(1.0 + annualRateDecimal, exponent);
        var factor = compound - 1.0;

        if (factor <= 0) {
            return BigDecimal.ZERO;
        }

        return outstanding.multiply(BigDecimal.valueOf(factor))
                .setScale(2, RoundingMode.HALF_UP);
    }
}
