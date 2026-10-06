package co.posinvent.infrastructure.adapters.out.persistence;

import co.posinvent.domain.exception.ResourceNotFoundException;
import co.posinvent.domain.model.StockAdjustment;
import co.posinvent.domain.repository.StockAdjustmentRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public class StockAdjustmentRepositoryAdapter implements StockAdjustmentRepository {

    private final StockAdjustmentJpaRepository jpa;
    private final StockAdjustmentMapper mapper;

    public StockAdjustmentRepositoryAdapter(StockAdjustmentJpaRepository jpa, StockAdjustmentMapper mapper) {
        this.jpa = jpa;
        this.mapper = mapper;
    }

    @Override
    public StockAdjustment save(StockAdjustment adjustment) {
        StockAdjustmentEntity entity;
        if (adjustment.id() != null) {
            entity = jpa.findById(adjustment.id())
                    .orElseThrow(() -> new ResourceNotFoundException("Ajuste", adjustment.id()));
            mapper.updateEntity(entity, adjustment);
        } else {
            entity = mapper.toEntity(adjustment);
        }
        return mapper.toDomain(jpa.save(entity));
    }

    @Override
    public Page<StockAdjustment> findAll(Pageable pageable) {
        return jpa.findAll(pageable).map(mapper::toDomain);
    }

    @Override
    public Page<StockAdjustment> findByProductId(UUID productId, Pageable pageable) {
        return jpa.findByProductIdOrderByCreatedAtDesc(productId, pageable).map(mapper::toDomain);
    }
}
