package co.posinvent.application.usecase;

import co.posinvent.application.annotation.Auditable;
import co.posinvent.application.dto.TaxRequest;
import co.posinvent.application.dto.TaxResponse;
import co.posinvent.domain.exception.BusinessException;
import co.posinvent.domain.exception.ResourceNotFoundException;
import co.posinvent.domain.model.Tax;
import co.posinvent.domain.repository.TaxRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class TaxUseCase {

    private final TaxRepository repository;

    public TaxUseCase(TaxRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<TaxResponse> list() {
        return repository.findAll().stream().map(TaxResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public TaxResponse getById(UUID id) {
        return repository.findById(id)
                .map(TaxResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Impuesto", id));
    }

    @Auditable(entityType = "TAX", action = "CREATE")
    @Transactional
    public TaxResponse create(TaxRequest request) {
        var code = normalizeCode(request.code());
        if (repository.existsByCode(code)) {
            throw new BusinessException("DUPLICATE_TAX_CODE",
                    "Ya existe un impuesto con el código: " + code);
        }
        return TaxResponse.from(repository.save(toDomain(null, code, request)));
    }

    @Auditable(entityType = "TAX", action = "UPDATE")
    @Transactional
    public TaxResponse update(UUID id, TaxRequest request) {
        var existing = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Impuesto", id));
        var code = normalizeCode(request.code());
        if (repository.existsByCodeAndIdNot(code, id)) {
            throw new BusinessException("DUPLICATE_TAX_CODE",
                    "Ya existe un impuesto con el código: " + code);
        }
        return TaxResponse.from(repository.save(toDomain(existing.id(), code, request)));
    }

    @Auditable(entityType = "TAX", action = "DELETE")
    @Transactional
    public void delete(UUID id) {
        var existing = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Impuesto", id));
        repository.save(new Tax(
                existing.id(), existing.code(), existing.description(), existing.percentage(),
                existing.observation(), existing.dianCode(), existing.siigoTaxId(),
                existing.sortOrder(), false));
    }

    private Tax toDomain(UUID id, String code, TaxRequest request) {
        return new Tax(
                id,
                code,
                request.description().trim(),
                request.percentage(),
                request.observation(),
                request.dianCode(),
                request.siigoTaxId(),
                request.sortOrder() != null ? request.sortOrder() : 0,
                request.active() == null || request.active()
        );
    }

    private String normalizeCode(String code) {
        return code.trim().toUpperCase();
    }
}
