package co.posinvent.infrastructure.adapters.out.persistence;

import co.posinvent.domain.exception.ResourceNotFoundException;
import co.posinvent.domain.model.InventoryStock;
import co.posinvent.domain.repository.StockRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
class StockRepositoryAdapter implements StockRepository {

    private final StockJpaRepository jpa;
    private final StockMapper mapper;

    StockRepositoryAdapter(StockJpaRepository jpa, StockMapper mapper) {
        this.jpa    = jpa;
        this.mapper = mapper;
    }

    @Override public InventoryStock save(InventoryStock stock) {
        InventoryStockEntity entity;
        if (stock.id() != null) {
            entity = jpa.findById(stock.id())
                    .orElseThrow(() -> new ResourceNotFoundException("Stock", stock.id()));
            mapper.updateEntity(entity, stock);
        } else {
            entity = mapper.toEntity(stock);
        }
        return mapper.toDomain(jpa.save(entity));
    }

    @Override public Optional<InventoryStock> findByProductBatchWarehouse(
            UUID productId, UUID batchId, UUID warehouseId) {
        return jpa.findByProductIdAndBatchIdAndWarehouseId(productId, batchId, warehouseId)
                  .map(mapper::toDomain);
    }

    @Override public List<InventoryStock> findByWarehouse(UUID warehouseId) {
        return jpa.findByWarehouseId(warehouseId).stream().map(mapper::toDomain).toList();
    }

    @Override public List<InventoryStock> findByProduct(UUID productId) {
        return jpa.findByProductId(productId).stream().map(mapper::toDomain).toList();
    }

    @Override public List<InventoryStock> findByBatch(UUID batchId) {
        return jpa.findByBatchId(batchId).stream().map(mapper::toDomain).toList();
    }

    @Override public List<InventoryStock> findAvailableByProductWarehouse(
            UUID productId, UUID warehouseId) {
        return jpa.findAvailableByProductWarehouse(productId, warehouseId)
                  .stream().map(mapper::toDomain).toList();
    }
}
