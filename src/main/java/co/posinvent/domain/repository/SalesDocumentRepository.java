package co.posinvent.domain.repository;

import co.posinvent.domain.model.SalesDocument;
import co.posinvent.domain.model.SalesDocumentStatus;
import co.posinvent.domain.model.SalesDocumentType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SalesDocumentRepository {

    SalesDocument save(SalesDocument document);

    Optional<SalesDocument> findById(UUID id);

    Page<SalesDocument> findAll(Pageable pageable);

    Page<SalesDocument> findByType(SalesDocumentType type, Pageable pageable);

    Page<SalesDocument> findByStatus(SalesDocumentStatus status, Pageable pageable);

    Page<SalesDocument> findByTypeAndStatus(SalesDocumentType type, SalesDocumentStatus status, Pageable pageable);

    /**
     * Lists documents applying the optional filters and, when {@code restricted}, the
     * caller's ownership rule in the database query. Pagination stays in SQL.
     */
    Page<SalesDocument> findVisible(SalesDocumentType type, SalesDocumentStatus status,
                                    boolean restricted, UUID actorId, Pageable pageable);

    Page<SalesDocument> findByClientId(UUID clientId, Pageable pageable);

    List<SalesDocument> findByShiftIdAndType(UUID shiftId, SalesDocumentType type);

    Optional<SalesDocument> findByIdWithItems(UUID id);

    List<SalesDocument> findBySourceDocumentId(UUID sourceDocumentId);

    List<SalesDocument> findBySourceDocumentIdAndType(UUID sourceDocumentId, SalesDocumentType type);
}
