package co.posinvent.domain.repository;

import co.posinvent.domain.model.GoodsReceipt;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;
import java.util.UUID;

public interface GoodsReceiptRepository {

    GoodsReceipt save(GoodsReceipt goodsReceipt);

    GoodsReceipt saveAndFlush(GoodsReceipt goodsReceipt);

    Optional<GoodsReceipt> findById(UUID id);

    /**
     * Resolves a previously registered receipt by its client-supplied idempotency key, so a
     * retried submission returns the existing receipt instead of creating a duplicate.
     */
    Optional<GoodsReceipt> findByIdempotencyKey(String idempotencyKey);

    Page<GoodsReceipt> findAll(Pageable pageable);

    Page<GoodsReceipt> findByOcId(UUID ocId, Pageable pageable);
}
