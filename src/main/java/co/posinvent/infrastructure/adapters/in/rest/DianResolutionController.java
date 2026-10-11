package co.posinvent.infrastructure.adapters.in.rest;

import co.posinvent.application.dto.DianResolutionRequest;
import co.posinvent.application.dto.DianResolutionResponse;
import co.posinvent.application.usecase.ManageDianResolutionUseCase;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/dian-resolutions")
@PreAuthorize("hasRole('ADMIN')")
public class DianResolutionController {

    private final ManageDianResolutionUseCase useCase;

    public DianResolutionController(ManageDianResolutionUseCase useCase) {
        this.useCase = useCase;
    }

    @GetMapping
    public List<DianResolutionResponse> listAll() {
        return useCase.listAll();
    }

    @GetMapping("/{id}")
    public DianResolutionResponse getById(@PathVariable UUID id) {
        return useCase.getById(id);
    }

    @PostMapping
    public DianResolutionResponse create(@Valid @RequestBody DianResolutionRequest request) {
        return useCase.create(request);
    }

    @PutMapping("/{id}")
    public DianResolutionResponse update(@PathVariable UUID id, @Valid @RequestBody DianResolutionRequest request) {
        return useCase.update(id, request);
    }

    @PostMapping("/{id}/activate")
    public DianResolutionResponse activate(@PathVariable UUID id) {
        return useCase.activate(id);
    }

    @PostMapping("/{id}/deactivate")
    public DianResolutionResponse deactivate(@PathVariable UUID id) {
        return useCase.deactivate(id);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable UUID id) {
        useCase.delete(id);
    }
}
