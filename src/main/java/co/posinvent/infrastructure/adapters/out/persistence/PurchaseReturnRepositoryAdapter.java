package co.posinvent.infrastructure.adapters.out.persistence;

import co.posinvent.domain.model.PurchaseReturn;
import co.posinvent.domain.repository.PurchaseReturnRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public class PurchaseReturnRepositoryAdapter implements PurchaseReturnRepository {

    private final PurchaseReturnJpaRepository jpa;
    private final PurchaseReturnMapper mapper;

    public PurchaseReturnRepositoryAdapter(PurchaseReturnJpaRepository jpa,
                                            PurchaseReturnMapper mapper) {
        this.jpa = jpa;
        this.mapper = mapper;
    }

    @Override
    public PurchaseReturn save(PurchaseReturn purchaseReturn) {
        var entity = toEntity(purchaseReturn);
        var saved = jpa.save(entity);
        return mapper.toDomain(saved);
    }

    private PurchaseReturnEntity toEntity(PurchaseReturn pr) {
        var entity = mapper.toEntity(pr);
        if (entity.getLines() != null) {
            entity.getLines().forEach(line -> line.setPurchaseReturn(entity));
        }
        return entity;
    }

    @Override
    public Optional<PurchaseReturn> findById(UUID id) {
        return jpa.findById(id).map(mapper::toDomain);
    }

    @Override
    public Page<PurchaseReturn> findAll(Pageable pageable) {
        return jpa.findAll(pageable).map(mapper::toDomain);
    }

    @Override
    public Optional<PurchaseReturn> findByReceiptId(UUID receiptId) {
        return jpa.findByReceiptId(receiptId).map(mapper::toDomain);
    }

    @Override
    public Optional<PurchaseReturn> findFirstByDocumentNumberStartingWith(String prefix) {
        return jpa.findFirstByDocumentNumberStartingWithOrderByDocumentNumberDesc(prefix)
                .map(mapper::toDomain);
    }
}
