package co.posinvent.infrastructure.adapters.in.rest;

import co.posinvent.application.dto.PurchaseRetentionConfigRequest;
import co.posinvent.application.dto.PurchaseRetentionConfigResponse;
import co.posinvent.application.usecase.PurchaseRetentionConfigUseCase;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/retention-configs")
@PreAuthorize("hasRole('ADMIN')")
public class PurchaseRetentionConfigController {

    private final PurchaseRetentionConfigUseCase useCase;

    public PurchaseRetentionConfigController(PurchaseRetentionConfigUseCase useCase) {
        this.useCase = useCase;
    }

    @GetMapping
    public List<PurchaseRetentionConfigResponse> listAll() {
        return useCase.listAll();
    }

    @GetMapping("/active")
    public List<PurchaseRetentionConfigResponse> listActive() {
        return useCase.listActive();
    }

    @GetMapping("/{id}")
    public PurchaseRetentionConfigResponse getById(@PathVariable UUID id) {
        return useCase.getById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PurchaseRetentionConfigResponse create(@Valid @RequestBody PurchaseRetentionConfigRequest request) {
        return useCase.create(request);
    }

    @PutMapping("/{id}")
    public PurchaseRetentionConfigResponse update(
            @PathVariable UUID id,
            @Valid @RequestBody PurchaseRetentionConfigRequest request) {
        return useCase.update(id, request);
    }

    @PatchMapping("/{id}/toggle")
    public PurchaseRetentionConfigResponse toggleActive(@PathVariable UUID id) {
        return useCase.toggleActive(id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        useCase.delete(id);
    }
}
