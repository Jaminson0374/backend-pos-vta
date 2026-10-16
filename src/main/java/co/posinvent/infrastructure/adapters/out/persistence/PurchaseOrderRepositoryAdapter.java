package co.posinvent.infrastructure.adapters.out.persistence;

import co.posinvent.domain.model.PurchaseOrder;
import co.posinvent.domain.model.PurchaseOrderStatus;
import co.posinvent.domain.repository.PurchaseOrderRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
class PurchaseOrderRepositoryAdapter implements PurchaseOrderRepository {

    private final PurchaseOrderJpaRepository jpa;
    private final PurchaseOrderMapper mapper;

    PurchaseOrderRepositoryAdapter(PurchaseOrderJpaRepository jpa, PurchaseOrderMapper mapper) {
        this.jpa = jpa;
        this.mapper = mapper;
    }

    @Override
    public PurchaseOrder save(PurchaseOrder order) {
        var entity = mapper.toEntity(order);
        // Sync bidirectional relationship for line items
        if (entity.getLines() != null) {
            entity.getLines().forEach(line -> line.setPurchaseOrder(entity));
        }
        var saved = jpa.save(entity);
        return mapper.toDomain(saved);
    }

    @Override
    public Optional<PurchaseOrder> findById(UUID id) {
        return jpa.findById(id).map(mapper::toDomain);
    }

    @Override
    public Optional<PurchaseOrder> findByIdForUpdate(UUID id) {
        return jpa.findByIdForUpdate(id).map(mapper::toDomain);
    }

    @Override
    public Page<PurchaseOrder> findAll(Pageable pageable) {
        return jpa.findAll(pageable).map(mapper::toDomain);
    }

    @Override
    public Page<PurchaseOrder> findByStatus(PurchaseOrderStatus status, Pageable pageable) {
        return jpa.findByStatus(status, pageable).map(mapper::toDomain);
    }

    @Override
    public Page<PurchaseOrder> findBySupplierId(UUID supplierId, Pageable pageable) {
        return jpa.findBySupplierId(supplierId, pageable).map(mapper::toDomain);
    }

    @Override
    public Page<PurchaseOrder> search(String q, Pageable pageable) {
        return jpa.search(q, pageable).map(mapper::toDomain);
    }

    @Override
    public Optional<PurchaseOrder> findFirstByDocumentNumberStartingWith(String prefix) {
        return jpa.findFirstByDocumentNumberStartingWithOrderByDocumentNumberDesc(prefix)
                .map(mapper::toDomain);
    }
}
