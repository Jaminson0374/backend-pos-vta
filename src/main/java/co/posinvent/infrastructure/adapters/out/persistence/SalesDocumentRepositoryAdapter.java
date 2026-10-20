package co.posinvent.infrastructure.adapters.out.persistence;

import co.posinvent.domain.model.SalesDocument;
import co.posinvent.domain.model.SalesDocumentStatus;
import co.posinvent.domain.model.SalesDocumentType;
import co.posinvent.domain.repository.SalesDocumentRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
class SalesDocumentRepositoryAdapter implements SalesDocumentRepository {

    private final SalesDocumentJpaRepository jpa;
    private final SalesDocumentMapper mapper;

    SalesDocumentRepositoryAdapter(SalesDocumentJpaRepository jpa, SalesDocumentMapper mapper) {
        this.jpa = jpa;
        this.mapper = mapper;
    }

    @Override
    public SalesDocument save(SalesDocument document) {
        var entity = mapper.toEntity(document);
        if (entity.getItems() != null) {
            entity.getItems().forEach(item -> item.setDocument(entity));
        }
        var saved = jpa.save(entity);
        return mapper.toDomain(saved);
    }

    @Override
    public Optional<SalesDocument> findById(UUID id) {
        return jpa.findById(id).map(mapper::toDomain);
    }

    @Override
    public Page<SalesDocument> findAll(Pageable pageable) {
        return jpa.findAll(pageable).map(mapper::toDomain);
    }

    @Override
    public Page<SalesDocument> findByType(SalesDocumentType type, Pageable pageable) {
        return jpa.findByType(type, pageable).map(mapper::toDomain);
    }

    @Override
    public Page<SalesDocument> findByStatus(SalesDocumentStatus status, Pageable pageable) {
        return jpa.findByStatus(status, pageable).map(mapper::toDomain);
    }

    @Override
    public Page<SalesDocument> findByTypeAndStatus(SalesDocumentType type, SalesDocumentStatus status, Pageable pageable) {
        return jpa.findByTypeAndStatus(type, status, pageable).map(mapper::toDomain);
    }

    @Override
    public Page<SalesDocument> findVisible(SalesDocumentType type, SalesDocumentStatus status,
                                           boolean restricted, UUID actorId, Pageable pageable) {
        Specification<SalesDocumentEntity> specification = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (type != null) {
                predicates.add(cb.equal(root.get("type"), type));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }

            if (restricted) {
                // A restriction matches when the actor created the document or effectively
                // owns the shift it belongs to (assignee wins over the opener).
                var ownedShift = query.subquery(UUID.class);
                var shift = ownedShift.from(ShiftEntity.class);
                ownedShift.select(shift.get("id")).where(
                        cb.equal(shift.get("id"), root.get("shiftId")),
                        cb.or(
                                cb.equal(shift.get("assignedTo"), actorId),
                                cb.and(
                                        cb.isNull(shift.get("assignedTo")),
                                        cb.equal(shift.get("userId"), actorId)
                                )
                        )
                );

                predicates.add(cb.or(
                        cb.equal(root.get("createdBy"), actorId),
                        cb.exists(ownedShift)
                ));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return jpa.findAll(specification, pageable).map(mapper::toDomain);
    }

    @Override
    public Page<SalesDocument> findByClientId(UUID clientId, Pageable pageable) {
        return jpa.findByClientId(clientId, pageable).map(mapper::toDomain);
    }

    @Override
    public List<SalesDocument> findByShiftIdAndType(UUID shiftId, SalesDocumentType type) {
        return jpa.findByShiftIdAndType(shiftId, type).stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public Optional<SalesDocument> findByIdWithItems(UUID id) {
        return jpa.findWithItemsById(id).map(mapper::toDomain);
    }

    @Override
    public List<SalesDocument> findBySourceDocumentId(UUID sourceDocumentId) {
        return jpa.findBySourceDocumentId(sourceDocumentId).stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public List<SalesDocument> findBySourceDocumentIdAndType(UUID sourceDocumentId, SalesDocumentType type) {
        return jpa.findBySourceDocumentIdAndType(sourceDocumentId, type).stream()
                .map(mapper::toDomain)
                .toList();
    }
}
