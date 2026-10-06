package co.posinvent.infrastructure.adapters.out.persistence;

import co.posinvent.domain.exception.ResourceNotFoundException;
import co.posinvent.domain.model.Batch;
import co.posinvent.domain.model.Batch.BatchStatus;
import co.posinvent.domain.repository.BatchRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
class BatchRepositoryAdapter implements BatchRepository {

    private final BatchJpaRepository jpa;
    private final BatchMapper mapper;

    BatchRepositoryAdapter(BatchJpaRepository jpa, BatchMapper mapper) {
        this.jpa    = jpa;
        this.mapper = mapper;
    }

    @Override public Batch save(Batch batch) {
        BatchEntity entity;
        if (batch.id() != null) {
            entity = jpa.findById(batch.id())
                    .orElseThrow(() -> new ResourceNotFoundException("Lote", batch.id()));
            mapper.updateEntity(entity, batch);
        } else {
            entity = mapper.toEntity(batch);
        }
        return mapper.toDomain(jpa.save(entity));
    }

    @Override public Optional<Batch> findById(UUID id) {
        return jpa.findById(id).map(mapper::toDomain);
    }

    @Override public Page<Batch> findAll(Pageable pageable) {
        return jpa.findAll(pageable).map(mapper::toDomain);
    }

    @Override public Page<Batch> findByStatus(BatchStatus status, Pageable pageable) {
        return jpa.findByStatus(status, pageable).map(mapper::toDomain);
    }

    @Override public Page<Batch> findByWarehouse(UUID warehouseId, Pageable pageable) {
        return jpa.findByWarehouseId(warehouseId, pageable).map(mapper::toDomain);
    }

    @Override public List<Batch> findBySourceReceiptId(UUID sourceReceiptId) {
        return jpa.findBySourceReceiptId(sourceReceiptId).stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public Page<Batch> findAllWithNames(Pageable pageable) {
        return jpa.findAllWithNames(pageable).map(this::toDomainWithNames);
    }

    @Override
    public Page<Batch> findByStatusWithNames(BatchStatus status, Pageable pageable) {
        return jpa.findByStatusWithNames(status, pageable).map(this::toDomainWithNames);
    }

    @Override
    public Optional<Batch> findByIdWithNames(UUID id) {
        return jpa.findByIdWithNames(id).map(this::toDomainWithNames);
    }

    @Override
    public List<Batch> findByParentBatchIdWithNames(UUID parentBatchId) {
        return jpa.findByParentBatchIdWithNames(parentBatchId).stream()
                .map(this::toDomainWithNames)
                .toList();
    }

    /**
     * Maps a projection row to an entity (setting transient name fields so MapStruct
     * picks them up), then converts to the enriched domain model.
     */
    private Batch toDomainWithNames(BatchWithNamesProjection p) {
        var entity = new BatchEntity();
        entity.setId(p.getId());
        entity.setProductId(p.getProductId());
        entity.setSupplierId(p.getSupplierId());
        entity.setWarehouseId(p.getWarehouseId());
        entity.setEntryDate(p.getEntryDate());
        entity.setInitialWeight(p.getInitialWeight());
        entity.setPurchaseCost(p.getPurchaseCost());
        entity.setStatus(p.getStatus() != null ? BatchStatus.valueOf(p.getStatus()) : null);
        entity.setNotes(p.getNotes());
        entity.setExpirationDate(p.getExpirationDate());
        entity.setCreatedBy(p.getCreatedBy());
        entity.setCreatedAt(OffsetDateTime.ofInstant(p.getCreatedAt(), ZoneOffset.UTC));
        entity.setSourceReceiptId(p.getSourceReceiptId());
        entity.setOcId(p.getOcId());
        entity.setParentBatchId(p.getParentBatchId());
        entity.setBatchType(p.getBatchType());
        entity.setUnitOfMeasureId(p.getUnitOfMeasureId());
        entity.setProductName(p.getProductName());
        entity.setSupplierName(p.getSupplierName());
        entity.setWarehouseName(p.getWarehouseName());
        return mapper.toDomain(entity);
    }
}
