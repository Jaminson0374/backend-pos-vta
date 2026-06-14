package co.posinvent.domain.repository;

import co.posinvent.domain.model.AccountsPayable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccountsPayableRepository {

    AccountsPayable save(AccountsPayable ap);

    Optional<AccountsPayable> findById(UUID id);

    Page<AccountsPayable> findBySupplierId(UUID supplierId, Pageable pageable);

    Page<AccountsPayable> findByStatus(AccountsPayable.ApStatus status, Pageable pageable);

    Page<AccountsPayable> findBySupplierIdAndStatus(UUID supplierId, AccountsPayable.ApStatus status,
                                                      Pageable pageable);

    List<AccountsPayable> findOverdueBefore(LocalDate date);

    Optional<AccountsPayable> findByDocumentId(UUID documentId);

    Page<AccountsPayable> findAll(Pageable pageable);
}
