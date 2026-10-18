package co.posinvent.domain.repository;

import co.posinvent.domain.model.AccountsReceivable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
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

    /**
     * Sum of {@code outstanding} over every AR row of the client. This is the single source of
     * truth for the customer balance, replacing the legacy static balance field on the receivable
     * side (column removed in A6).
     */
    BigDecimal sumOutstandingByClientId(UUID clientId);

    /**
     * Batch version of {@link #sumOutstandingByClientId(UUID)}: derived outstanding keyed by
     * client id, for every client that has at least one AR row.
     */
    Map<UUID, BigDecimal> sumOutstandingByClientIds(Collection<UUID> clientIds);
}
