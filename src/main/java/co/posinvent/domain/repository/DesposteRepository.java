package co.posinvent.domain.repository;

import co.posinvent.domain.model.Desposte;
import org.springframework.data.domain.Page;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface DesposteRepository {

    Desposte save(Desposte desposte);

    Optional<Desposte> findById(UUID id);

    /**
     * Paginated lookup of despostes by creation date, ordered newest first.
     * A {@code null} bound leaves that side of the range open.
     */
    Page<Desposte> findPage(LocalDate from, LocalDate to, int page, int size);
}
