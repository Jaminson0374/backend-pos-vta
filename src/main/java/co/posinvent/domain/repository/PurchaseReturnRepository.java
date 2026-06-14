package co.posinvent.domain.repository;

import co.posinvent.domain.model.PurchaseReturn;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;
import java.util.UUID;

public interface PurchaseReturnRepository {

    PurchaseReturn save(PurchaseReturn purchaseReturn);

    Optional<PurchaseReturn> findById(UUID id);

    Page<PurchaseReturn> findAll(Pageable pageable);

    Optional<PurchaseReturn> findByReceiptId(UUID receiptId);

    Optional<PurchaseReturn> findFirstByDocumentNumberStartingWith(String prefix);
}
