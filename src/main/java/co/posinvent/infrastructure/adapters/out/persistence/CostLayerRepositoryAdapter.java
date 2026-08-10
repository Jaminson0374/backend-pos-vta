package co.posinvent.infrastructure.adapters.out.persistence;

import co.posinvent.domain.model.CostLayer;
import co.posinvent.domain.repository.CostLayerRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Repository
public class CostLayerRepositoryAdapter implements CostLayerRepository {

    private final CostLayerJpaRepository jpa;
    private final CostLayerMapper mapper;

    public CostLayerRepositoryAdapter(CostLayerJpaRepository jpa, CostLayerMapper mapper) {
        this.jpa = jpa;
        this.mapper = mapper;
    }

    @Override
    public CostLayer save(CostLayer layer) {
        return mapper.toDomain(jpa.save(mapper.toEntity(layer)));
    }

    @Override
    public List<CostLayer> findByProductBatchWarehouse(UUID productId, UUID batchId, UUID warehouseId) {
        return jpa.findByProductIdAndBatchIdAndWarehouseIdOrderByEntryDateAsc(productId, batchId, warehouseId)
                .stream().map(mapper::toDomain).toList();
    }

    @Override
    public List<CostLayer> findByProductBatchWarehouseFefo(UUID productId, UUID batchId, UUID warehouseId) {
        return jpa.findByProductBatchWarehouseFefo(productId, batchId, warehouseId)
                .stream().map(mapper::toDomain).toList();
    }

    @Override
    @Transactional
    public void deleteAllByProductBatchWarehouse(UUID productId, UUID batchId, UUID warehouseId) {
        jpa.deleteAllByProductBatchWarehouse(productId, batchId, warehouseId);
    }
}
