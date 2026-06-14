package co.posinvent.infrastructure.adapters.out.persistence;

import co.posinvent.domain.model.SalesDocumentStatus;
import co.posinvent.domain.model.SalesDocumentType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface SalesDocumentJpaRepository extends JpaRepository<SalesDocumentEntity, UUID> {

    Page<SalesDocumentEntity> findByType(SalesDocumentType type, Pageable pageable);

    Page<SalesDocumentEntity> findByStatus(SalesDocumentStatus status, Pageable pageable);

    Page<SalesDocumentEntity> findByTypeAndStatus(SalesDocumentType type, SalesDocumentStatus status, Pageable pageable);

    Page<SalesDocumentEntity> findByClientId(UUID clientId, Pageable pageable);

    List<SalesDocumentEntity> findByShiftIdAndType(UUID shiftId, SalesDocumentType type);

    @EntityGraph(attributePaths = "items")
    Optional<SalesDocumentEntity> findWithItemsById(UUID id);

    List<SalesDocumentEntity> findBySourceDocumentId(UUID sourceDocumentId);

    List<SalesDocumentEntity> findBySourceDocumentIdAndType(UUID sourceDocumentId, SalesDocumentType type);
}
