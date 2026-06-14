package co.posinvent.domain.repository;

import co.posinvent.domain.model.AccountsReceivable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccountsReceivableRepository {

    AccountsReceivable save(AccountsReceivable ar);

    Optional<AccountsReceivable> findById(UUID id);

    Page<AccountsReceivable> findByClientId(UUID clientId, Pageable pageable);

    Page<AccountsReceivable> findByStatus(AccountsReceivable.ArStatus status, Pageable pageable);

    Page<AccountsReceivable> findByClientIdAndStatus(UUID clientId, AccountsReceivable.ArStatus status,
                                                      Pageable pageable);

    List<AccountsReceivable> findOverdueBefore(LocalDate date);

    /**
     * Find all AR that are overdue, excluding those within the grace period.
     * @param today reference date (usually LocalDate.now())
     * @param graceDays number of days of grace before interest starts accruing
     * @return list of AR with dueDate &lt; (today - graceDays) and status != PAID
     */
    List<AccountsReceivable> findOverdueBeforeGrace(LocalDate today, int graceDays);

    Optional<AccountsReceivable> findByDocumentId(UUID documentId);

    List<AccountsReceivable> findByStatusAndOutstandingGreaterThan(AccountsReceivable.ArStatus status, java.math.BigDecimal minOutstanding, LocalDate dueDateBefore);

    Page<AccountsReceivable> findAll(Pageable pageable);
}
