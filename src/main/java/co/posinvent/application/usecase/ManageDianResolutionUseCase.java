package co.posinvent.application.usecase;

import co.posinvent.application.dto.DianResolutionRequest;
import co.posinvent.application.dto.DianResolutionResponse;
import co.posinvent.domain.exception.BusinessException;
import co.posinvent.domain.exception.ResourceNotFoundException;
import co.posinvent.domain.model.DianResolution;
import co.posinvent.domain.repository.DianResolutionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
public class ManageDianResolutionUseCase {

    private final DianResolutionRepository repository;

    public ManageDianResolutionUseCase(DianResolutionRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<DianResolutionResponse> listAll() {
        return repository.findAll().stream()
                .map(DianResolutionResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public DianResolutionResponse getById(UUID id) {
        return repository.findById(id)
                .map(DianResolutionResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Resolución DIAN", id));
    }

    @Transactional
    public DianResolutionResponse create(DianResolutionRequest request) {
        validate(request, null);

        var resolution = new DianResolution(
                null,
                request.resolutionNumber(),
                request.resolutionDate(),
                request.validFrom(),
                request.validTo(),
                request.prefix() != null ? request.prefix() : "",
                request.rangeFrom(),
                request.rangeTo(),
                request.softwarePin(),
                request.active() != null ? request.active() : false,
                null
        );

        var saved = repository.save(resolution);
        return DianResolutionResponse.from(saved);
    }

    @Transactional
    public DianResolutionResponse update(UUID id, DianResolutionRequest request) {
        var existing = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Resolución DIAN", id));

        validate(request, id);

        var updated = new DianResolution(
                id,
                request.resolutionNumber(),
                request.resolutionDate(),
                request.validFrom(),
                request.validTo(),
                request.prefix() != null ? request.prefix() : "",
                request.rangeFrom(),
                request.rangeTo(),
                request.softwarePin(),
                request.active() != null ? request.active() : existing.active(),
                null
        );

        var saved = repository.save(updated);
        return DianResolutionResponse.from(saved);
    }

    @Transactional
    public DianResolutionResponse activate(UUID id) {
        var resolution = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Resolución DIAN", id));

        repository.deactivateAll();

        var activated = new DianResolution(
                id,
                resolution.resolutionNumber(),
                resolution.resolutionDate(),
                resolution.validFrom(),
                resolution.validTo(),
                resolution.prefix(),
                resolution.rangeFrom(),
                resolution.rangeTo(),
                resolution.softwarePin(),
                true,
                null
        );

        var saved = repository.save(activated);
        return DianResolutionResponse.from(saved);
    }

    @Transactional
    public DianResolutionResponse deactivate(UUID id) {
        var resolution = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Resolución DIAN", id));

        var deactivated = new DianResolution(
                id,
                resolution.resolutionNumber(),
                resolution.resolutionDate(),
                resolution.validFrom(),
                resolution.validTo(),
                resolution.prefix(),
                resolution.rangeFrom(),
                resolution.rangeTo(),
                resolution.softwarePin(),
                false,
                resolution.createdAt()
        );

        var saved = repository.save(deactivated);
        return DianResolutionResponse.from(saved);
    }

    @Transactional
    public void delete(UUID id) {
        if (repository.findById(id).isEmpty()) {
            throw new ResourceNotFoundException("Resolución DIAN", id);
        }
        repository.deleteById(id);
    }

    private void validate(DianResolutionRequest request, UUID excludeId) {
        if (request.rangeFrom() != null && request.rangeTo() != null
                && request.rangeFrom() > request.rangeTo()) {
            throw new BusinessException("DIAN_RANGE_INVALID",
                    "El rango 'desde' no puede ser mayor que el 'hasta'");
        }

        if (request.validFrom() != null && request.validTo() != null
                && request.validFrom().isAfter(request.validTo())) {
            throw new BusinessException("DIAN_DATE_RANGE",
                    "La fecha 'desde' no puede ser posterior a la fecha 'hasta'");
        }

        var prefix = normalizePrefix(request.prefix());
        for (var other : repository.findAll()) {
            if (excludeId != null && excludeId.equals(other.id())) {
                continue;
            }
            if (overlaps(prefix, request.validFrom(), request.validTo(),
                    request.rangeFrom(), request.rangeTo(), other)) {
                throw new BusinessException("DIAN_RESOLUTION_OVERLAP",
                        "Ya existe una resolución con el mismo prefijo y rangos de vigencia/numeración solapados");
            }
        }
    }

    private boolean overlaps(String prefix, LocalDate validFrom, LocalDate validTo,
                             Long rangeFrom, Long rangeTo, DianResolution other) {
        if (!normalizePrefix(other.prefix()).equals(prefix)) {
            return false;
        }
        var datesIntersect = validFrom != null && validTo != null
                && other.validFrom() != null && other.validTo() != null
                && !validFrom.isAfter(other.validTo())
                && !validTo.isBefore(other.validFrom());
        var rangesIntersect = rangeFrom != null && rangeTo != null
                && other.rangeFrom() != null && other.rangeTo() != null
                && !(rangeFrom > other.rangeTo())
                && !(rangeTo < other.rangeFrom());
        return datesIntersect && rangesIntersect;
    }

    private static String normalizePrefix(String prefix) {
        return prefix != null ? prefix : "";
    }
}
