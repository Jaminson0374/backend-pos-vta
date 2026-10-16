package co.posinvent.application.usecase;

import co.posinvent.domain.exception.BusinessException;
import co.posinvent.domain.model.AccountingPeriod;
import co.posinvent.domain.repository.AccountingPeriodRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;

/**
 * Manages the {@code OPEN}/{@code CLOSED} lifecycle of monthly accounting periods (A5).
 *
 * <p>Posting is only blocked when a period is explicitly {@code CLOSED}; a period with no
 * stored row is open by default so existing flows keep working.</p>
 */
@Service
public class AccountingPeriodUseCase {

    private static final DateTimeFormatter PERIOD_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");

    private final AccountingPeriodRepository repo;

    public AccountingPeriodUseCase(AccountingPeriodRepository repo) {
        this.repo = repo;
    }

    public List<AccountingPeriod> list() {
        return repo.findAll();
    }

    /**
     * Closes {@code period} ({@code YYYY-MM}). If the row does not exist it is created and
     * immediately closed, keeping the operation idempotent.
     */
    @Transactional
    public AccountingPeriod close(String period, UUID userId) {
        String normalized = normalize(period);
        OffsetDateTime openedAt = repo.findById(normalized)
                .map(AccountingPeriod::openedAt)
                .orElseGet(OffsetDateTime::now);
        var closed = new AccountingPeriod(
                normalized,
                AccountingPeriod.Status.CLOSED,
                openedAt,
                OffsetDateTime.now(),
                userId);
        return repo.save(closed);
    }

    /**
     * Rejects a journal entry dated inside an explicitly {@code CLOSED} period.
     *
     * @throws BusinessException {@code ACCOUNTING_PERIOD_CLOSED} when the entry date's period is closed
     */
    public void assertOpen(LocalDate date) {
        String period = date.format(PERIOD_FORMAT);
        repo.findById(period)
                .filter(AccountingPeriod::isClosed)
                .ifPresent(closed -> {
                    throw new BusinessException("ACCOUNTING_PERIOD_CLOSED",
                            "El período contable " + period + " está cerrado");
                });
    }

    /** Normalizes a {@code YYYY-MM} key, rejecting any other format. */
    private static String normalize(String period) {
        try {
            return YearMonth.parse(period, PERIOD_FORMAT).format(PERIOD_FORMAT);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("El período debe tener el formato YYYY-MM: " + period);
        }
    }
}
