package co.posinvent.infrastructure.adapters.out.persistence;

import co.posinvent.domain.model.GoodsReceipt;
import co.posinvent.domain.repository.GoodsReceiptRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Repository
class GoodsReceiptRepositoryAdapter implements GoodsReceiptRepository {

    private final GoodsReceiptJpaRepository jpa;
    private final GoodsReceiptMapper mapper;

    GoodsReceiptRepositoryAdapter(GoodsReceiptJpaRepository jpa, GoodsReceiptMapper mapper) {
        this.jpa = jpa;
        this.mapper = mapper;
    }

    @Override
    public GoodsReceipt save(GoodsReceipt receipt) {
        var entity = toEntity(receipt);
        var saved = jpa.save(entity);
        return mapper.toDomain(saved);
    }

    @Override
    public GoodsReceipt saveAndFlush(GoodsReceipt receipt) {
        var entity = toEntity(receipt);
        var saved = jpa.saveAndFlush(entity);
        return mapper.toDomain(saved);
    }

    private GoodsReceiptEntity toEntity(GoodsReceipt receipt) {
        var entity = mapper.toEntity(receipt);
        // Sync bidirectional relationship for line items
        if (entity.getLines() != null) {
            entity.getLines().forEach(line -> line.setGoodsReceipt(entity));
        }
        return entity;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<GoodsReceipt> findById(UUID id) {
        return jpa.findById(id).map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<GoodsReceipt> findAll(Pageable pageable) {
        return jpa.findAll(pageable).map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<GoodsReceipt> findByOcId(UUID ocId, Pageable pageable) {
        return jpa.findByOcId(ocId, pageable).map(mapper::toDomain);
    }
}
