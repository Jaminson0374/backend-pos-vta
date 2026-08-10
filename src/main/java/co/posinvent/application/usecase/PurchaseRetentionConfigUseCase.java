package co.posinvent.application.usecase;

import co.posinvent.application.dto.PurchaseRetentionConfigRequest;
import co.posinvent.application.dto.PurchaseRetentionConfigResponse;
import co.posinvent.domain.exception.BusinessException;
import co.posinvent.domain.model.PurchaseRetentionConfig;
import co.posinvent.domain.repository.PurchaseRetentionConfigRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class PurchaseRetentionConfigUseCase {

    private final PurchaseRetentionConfigRepository repository;

    public PurchaseRetentionConfigUseCase(PurchaseRetentionConfigRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<PurchaseRetentionConfigResponse> listAll() {
        return repository.findAll().stream().map(PurchaseRetentionConfigResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<PurchaseRetentionConfigResponse> listActive() {
        return repository.findAllActive().stream().map(PurchaseRetentionConfigResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public PurchaseRetentionConfigResponse getById(UUID id) {
        return repository.findById(id)
                .map(PurchaseRetentionConfigResponse::from)
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "Configuración de retención no encontrada."));
    }

    @Transactional
    public PurchaseRetentionConfigResponse create(PurchaseRetentionConfigRequest request) {
        if (repository.existsByCode(request.code())) {
            throw new BusinessException("DUPLICATE_CODE",
                    "Ya existe una configuración con el código " + request.code());
        }
        var config = new PurchaseRetentionConfig(
                null, request.code(), request.name(), request.description(),
                request.rate(), request.baseMin(),
                request.appliesToTaxRegime(), request.appliesToPersonType(),
                true, request.sortOrder(), null, null
        );
        return PurchaseRetentionConfigResponse.from(repository.save(config));
    }

    @Transactional
    public PurchaseRetentionConfigResponse update(UUID id, PurchaseRetentionConfigRequest request) {
        var existing = repository.findById(id)
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "Configuración de retención no encontrada."));

        if (!existing.code().equals(request.code()) && repository.existsByCode(request.code())) {
            throw new BusinessException("DUPLICATE_CODE",
                    "Ya existe una configuración con el código " + request.code());
        }

        var updated = new PurchaseRetentionConfig(
                existing.id(), request.code(), request.name(), request.description(),
                request.rate(), request.baseMin(),
                request.appliesToTaxRegime(), request.appliesToPersonType(),
                true, request.sortOrder(),
                existing.createdAt(), null
        );
        return PurchaseRetentionConfigResponse.from(repository.save(updated));
    }

    @Transactional
    public PurchaseRetentionConfigResponse toggleActive(UUID id) {
        var existing = repository.findById(id)
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "Configuración de retención no encontrada."));

        var toggled = new PurchaseRetentionConfig(
                existing.id(), existing.code(), existing.name(), existing.description(),
                existing.rate(), existing.baseMin(),
                existing.appliesToTaxRegime(), existing.appliesToPersonType(),
                !existing.active(), existing.sortOrder(),
                existing.createdAt(), null
        );
        return PurchaseRetentionConfigResponse.from(repository.save(toggled));
    }

    @Transactional
    public void delete(UUID id) {
        if (repository.findById(id).isEmpty()) {
            throw new BusinessException("NOT_FOUND", "Configuración de retención no encontrada.");
        }
        repository.deleteById(id);
    }
}
