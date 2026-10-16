package co.posinvent.domain.repository;

import co.posinvent.domain.model.AccountingPeriod;

import java.util.List;
import java.util.Optional;

/**
 * Outbound port for accounting periods. {@link #findById(String)} receives the
 * {@code YYYY-MM} period key.
 */
public interface AccountingPeriodRepository {
    Optional<AccountingPeriod> findById(String period);
    AccountingPeriod save(AccountingPeriod period);
    List<AccountingPeriod> findAll();
}
