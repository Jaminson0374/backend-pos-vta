package co.posinvent.domain.repository;

import co.posinvent.domain.model.DianResolution;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DianResolutionRepository {
    DianResolution save(DianResolution resolution);
    Optional<DianResolution> findById(UUID id);
    List<DianResolution> findAll();
    void deleteById(UUID id);
    Optional<DianResolution> findActive();

    /** Loads the active resolution with a pessimistic write lock, for concurrency-safe numbering. */
    Optional<DianResolution> findActiveForUpdate();

    void deactivateAll();
}
