package co.posinvent.application.usecase;

import co.posinvent.application.dto.AnimalRequest;
import co.posinvent.application.dto.AnimalResponse;
import co.posinvent.application.dto.PageResponse;
import co.posinvent.application.port.in.AnimalPort;
import co.posinvent.domain.exception.BusinessException;
import co.posinvent.domain.exception.ResourceNotFoundException;
import co.posinvent.domain.model.Animal;
import co.posinvent.domain.model.Animal.AnimalStatus;
import co.posinvent.domain.repository.AnimalRepository;
import co.posinvent.domain.repository.ThirdPartyRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class AnimalUseCase implements AnimalPort {

    private final AnimalRepository animalRepository;
    private final ThirdPartyRepository thirdPartyRepository;

    public AnimalUseCase(
            AnimalRepository animalRepository,
            ThirdPartyRepository thirdPartyRepository
    ) {
        this.animalRepository = animalRepository;
        this.thirdPartyRepository = thirdPartyRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<AnimalResponse> list(Pageable pageable) {
        return PageResponse.from(animalRepository.findAll(pageable), AnimalResponse::from);
    }

    @Transactional(readOnly = true)
    public PageResponse<AnimalResponse> listByStatus(AnimalStatus status, Pageable pageable) {
        return PageResponse.from(animalRepository.findByStatus(status, pageable), AnimalResponse::from);
    }

    @Transactional(readOnly = true)
    public PageResponse<AnimalResponse> searchByIcaLot(String search, Pageable pageable) {
        return PageResponse.from(animalRepository.searchByIcaLot(search, pageable), AnimalResponse::from);
    }

    @Transactional(readOnly = true)
    public AnimalResponse getById(UUID id) {
        return animalRepository.findById(id)
                .map(AnimalResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Animal", id));
    }

    @Transactional
    public AnimalResponse create(AnimalRequest request, UUID operatorId) {
        // Validar que el proveedor exista
        thirdPartyRepository.findById(request.supplierId())
                .orElseThrow(() -> new ResourceNotFoundException("Proveedor", request.supplierId()));

        var animal = new Animal(
                null,
                request.icaLotNumber(),
                request.supplierId(),
                request.species(),
                request.liveWeight(),
                request.receptionDate(),
                AnimalStatus.RECEIVED,
                request.notes(),
                operatorId,
                null,
                null
        );

        return AnimalResponse.from(animalRepository.save(animal));
    }

    @Transactional
    public AnimalResponse update(UUID id, AnimalRequest request) {
        var existing = animalRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Animal", id));

        // Un animal ya sacrificado no se puede editar
        if (existing.status() == AnimalStatus.SLAUGHTERED) {
            throw new BusinessException(
                    "ANIMAL_IMMUTABLE",
                    "Un animal sacrificado no puede modificarse"
            );
        }

        // Validar que el proveedor exista
        thirdPartyRepository.findById(request.supplierId())
                .orElseThrow(() -> new ResourceNotFoundException("Proveedor", request.supplierId()));

        var updated = new Animal(
                existing.id(),
                request.icaLotNumber(),
                request.supplierId(),
                request.species(),
                request.liveWeight(),
                request.receptionDate(),
                existing.status(),
                request.notes(),
                existing.createdBy(),
                existing.createdAt(),
                null
        );

        return AnimalResponse.from(animalRepository.save(updated));
    }

    @Transactional
    public void delete(UUID id) {
        var animal = animalRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Animal", id));
        if (animal.status() == AnimalStatus.SLAUGHTERED) {
            throw new BusinessException(
                    "ANIMAL_IMMUTABLE",
                    "Un animal sacrificado no puede eliminarse"
            );
        }
        animalRepository.deleteById(id);
    }
}
