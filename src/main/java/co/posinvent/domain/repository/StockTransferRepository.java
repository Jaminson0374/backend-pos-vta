package co.posinvent.domain.repository;

import co.posinvent.domain.model.StockTransfer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;
import java.util.UUID;

public interface StockTransferRepository {

    StockTransfer save(StockTransfer transfer);

    Optional<StockTransfer> findById(UUID id);

    Page<StockTransfer> findAll(Pageable pageable);

    /**
     * Lists transfers applying, when {@code restricted}, the actor's warehouse membership in the
     * database query. Pagination stays in SQL.
     */
    Page<StockTransfer> findVisible(UUID actorId, boolean restricted, Pageable pageable);
}
