package co.posinvent.application.usecase;

import co.posinvent.application.dto.PriceListRequest;
import co.posinvent.application.dto.PriceListResponse;
import co.posinvent.application.port.in.PriceListPort;
import co.posinvent.domain.exception.BusinessException;
import co.posinvent.domain.exception.ResourceNotFoundException;
import co.posinvent.domain.model.PriceList;
import co.posinvent.domain.repository.PriceListRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class PriceListUseCase implements PriceListPort {

    private final PriceListRepository repository;

    public PriceListUseCase(PriceListRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<PriceListResponse> listAll() {
        return repository.findAllActive().stream().map(PriceListResponse::from).toList();
    }

    @Transactional
    public PriceListResponse create(PriceListRequest request) {
        if (repository.existsByCode(request.code())) {
            throw new BusinessException("DUPLICATE_CODE", "Ya existe una lista de precios con ese código.");
        }
        if (repository.existsByName(request.name())) {
            throw new BusinessException("DUPLICATE_NAME", "Ya existe una lista de precios con ese nombre.");
        }

        var entity = new PriceList(null, request.code(), request.name(), request.description(), true, null);
        return PriceListResponse.from(repository.save(entity));
    }

    @Transactional
    public PriceListResponse update(UUID id, PriceListRequest request) {
        var existing = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Lista de precios", id));

        if (repository.existsByCodeAndIdNot(request.code(), id)) {
            throw new BusinessException("DUPLICATE_CODE", "Ya existe una lista de precios con ese código.");
        }
        if (repository.existsByNameAndIdNot(request.name(), id)) {
            throw new BusinessException("DUPLICATE_NAME", "Ya existe una lista de precios con ese nombre.");
        }

        var updated = new PriceList(
                existing.id(),
                request.code(),
                request.name(),
                request.description(),
                existing.active(),
                existing.createdAt()
        );

        return PriceListResponse.from(repository.save(updated));
    }

    @Transactional
    public void deactivate(UUID id) {
        var existing = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Lista de precios", id));

        if (!existing.active()) {
            return;
        }

        repository.save(new PriceList(
                existing.id(),
                existing.code(),
                existing.name(),
                existing.description(),
                false,
                existing.createdAt()
        ));
    }
}
