package co.posinvent.infrastructure.adapters.out.persistence;

import co.posinvent.domain.exception.ResourceNotFoundException;
import co.posinvent.domain.model.StockTransfer;
import co.posinvent.domain.repository.StockTransferRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public class StockTransferRepositoryAdapter implements StockTransferRepository {

    private final StockTransferJpaRepository jpa;
    private final StockTransferMapper mapper;

    public StockTransferRepositoryAdapter(StockTransferJpaRepository jpa, StockTransferMapper mapper) {
        this.jpa = jpa;
        this.mapper = mapper;
    }

    @Override
    public StockTransfer save(StockTransfer transfer) {
        StockTransferEntity entity;
        if (transfer.id() != null) {
            entity = jpa.findByIdWithItems(transfer.id())
                    .orElseThrow(() -> new ResourceNotFoundException("Traslado", transfer.id()));
            mapper.updateEntity(entity, transfer);
        } else {
            entity = mapper.toEntity(transfer);
        }
        if (transfer.items() != null) {
            var itemEntities = new java.util.ArrayList<StockTransferItemEntity>();
            for (var item : transfer.items()) {
                var ie = mapper.toItemEntity(item);
                ie.setTransfer(entity);
                itemEntities.add(ie);
            }
            entity.getItems().clear();
            entity.getItems().addAll(itemEntities);
        }
        return mapper.toDomain(jpa.save(entity));
    }

    @Override
    public Optional<StockTransfer> findById(UUID id) {
        return jpa.findByIdWithItems(id).map(mapper::toDomain);
    }

    @Override
    public Page<StockTransfer> findAll(Pageable pageable) {
        return jpa.findAll(pageable).map(mapper::toDomain);
    }

    @Override
    public Page<StockTransfer> findVisible(UUID actorId, boolean restricted, Pageable pageable) {
        Specification<StockTransferEntity> specification = (root, query, cb) -> {
            if (!restricted) {
                return cb.conjunction();
            }

            // EXISTS (select 1 from user_warehouses uw
            //         where uw.user_id = :actorId
            //           and (uw.warehouse_id = t.source_warehouse_id
            //                or uw.warehouse_id = t.target_warehouse_id))
            var membership = query.subquery(UUID.class);
            var userWarehouse = membership.from(UserWarehouseEntity.class);
            membership.select(userWarehouse.get("id").get("warehouseId")).where(
                    cb.equal(userWarehouse.get("id").get("userId"), actorId),
                    cb.or(
                            cb.equal(userWarehouse.get("id").get("warehouseId"),
                                    root.get("sourceWarehouseId")),
                            cb.equal(userWarehouse.get("id").get("warehouseId"),
                                    root.get("targetWarehouseId"))
                    )
            );

            return cb.exists(membership);
        };

        return jpa.findAll(specification, pageable).map(mapper::toDomain);
    }
}
