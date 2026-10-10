package co.posinvent.application.port.in;

import co.posinvent.application.dto.AnimalRequest;
import co.posinvent.application.dto.AnimalResponse;
import co.posinvent.application.dto.PageResponse;
import co.posinvent.domain.model.Animal.AnimalStatus;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface AnimalPort {

    PageResponse<AnimalResponse> list(Pageable pageable);

    PageResponse<AnimalResponse> listByStatus(AnimalStatus status, Pageable pageable);

    PageResponse<AnimalResponse> searchByIcaLot(String search, Pageable pageable);

    AnimalResponse getById(UUID id);

    AnimalResponse create(AnimalRequest request, UUID operatorId);

    AnimalResponse update(UUID id, AnimalRequest request);

    void delete(UUID id);
}
