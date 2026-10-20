package co.posinvent.infrastructure.adapters.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface StockTransferJpaRepository
        extends JpaRepository<StockTransferEntity, UUID>,
        JpaSpecificationExecutor<StockTransferEntity> {

    @Query("SELECT t FROM StockTransferEntity t LEFT JOIN FETCH t.items WHERE t.id = :id")
    Optional<StockTransferEntity> findByIdWithItems(@Param("id") UUID id);
}
