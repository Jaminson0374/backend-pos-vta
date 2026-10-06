package co.posinvent.infrastructure.adapters.out.persistence;

import co.posinvent.domain.exception.ResourceNotFoundException;
import co.posinvent.domain.model.InventoryMovement;
import co.posinvent.domain.model.MovementType;
import co.posinvent.domain.repository.KardexRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

@Repository
public class KardexRepositoryAdapter implements KardexRepository {

    private static final OffsetDateTime EPOCH = OffsetDateTime.of(1900, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
    private static final OffsetDateTime FAR_FUTURE = OffsetDateTime.of(2100, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);

    private final KardexJpaRepository jpa;
    private final KardexMapper mapper;

    public KardexRepositoryAdapter(KardexJpaRepository jpa, KardexMapper mapper) {
        this.jpa = jpa;
        this.mapper = mapper;
    }

    @Override
    public InventoryMovement save(InventoryMovement movement) {
        InventoryMovementEntity entity;
        if (movement.id() != null) {
            entity = jpa.findById(movement.id())
                    .orElseThrow(() -> new ResourceNotFoundException("Movimiento", movement.id()));
            mapper.updateEntity(entity, movement);
        } else {
            entity = mapper.toEntity(movement);
        }
        return mapper.toDomain(jpa.save(entity));
    }

    @Override
    public Page<InventoryMovement> search(
            UUID productId, UUID batchId, UUID warehouseId,
            MovementType movementType, OffsetDateTime from, OffsetDateTime to, Pageable pageable
    ) {
        String type = movementType != null ? movementType.name() : null;
        var fromDate = from != null ? from : EPOCH;
        var toDate = to != null ? to : FAR_FUTURE;
        return jpa.search(productId, batchId, warehouseId, type, fromDate, toDate, pageable)
                .map(mapper::toDomain);
    }

    @Override
    public Optional<BigDecimal> getUnitCost(UUID productId, UUID warehouseId, String costingMethod) {
        if ("FIFO".equalsIgnoreCase(costingMethod)) {
            return jpa.getFifoCost(productId);
        }
        return jpa.getWeightedAverageCost(productId);
    }

    @Override
    public BigDecimal getCurrentStock(UUID productId, UUID warehouseId) {
        var stock = jpa.getCurrentStock(productId, warehouseId);
        return stock != null ? stock : BigDecimal.ZERO;
    }
}
