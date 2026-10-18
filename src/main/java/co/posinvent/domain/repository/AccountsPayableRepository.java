package co.posinvent.domain.repository;

import co.posinvent.domain.model.AccountsPayable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccountsPayableRepository {

    AccountsPayable save(AccountsPayable ap);

    Optional<AccountsPayable> findById(UUID id);

    Optional<AccountsPayable> findByDocumentIdForUpdate(UUID documentId);

    /**
     * Derived supplier balance: the sum of the outstanding balance of every CxP row for the
     * supplier. This is the single source of truth for what is owed to the supplier, replacing
     * the legacy static balance field on the payable side (column removed in A6).
     */
    BigDecimal sumOutstandingBySupplierId(UUID supplierId);

    Page<AccountsPayable> findBySupplierId(UUID supplierId, Pageable pageable);

    Page<AccountsPayable> findByStatus(AccountsPayable.ApStatus status, Pageable pageable);

    Page<AccountsPayable> findBySupplierIdAndStatus(UUID supplierId, AccountsPayable.ApStatus status,
                                                      Pageable pageable);

    List<AccountsPayable> findOverdueBefore(LocalDate date);

    Optional<AccountsPayable> findByDocumentId(UUID documentId);

    Page<AccountsPayable> findAll(Pageable pageable);
}
