package co.posinvent.infrastructure.adapters.in.rest;

import co.posinvent.application.dto.TaxRequest;
import co.posinvent.application.dto.TaxResponse;
import co.posinvent.application.usecase.TaxUseCase;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/taxes")
public class TaxController {

    private final TaxUseCase useCase;

    public TaxController(TaxUseCase useCase) {
        this.useCase = useCase;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','AUXILIAR','ALMACENISTA','CONTADOR')")
    public ResponseEntity<List<TaxResponse>> list() {
        return ResponseEntity.ok(useCase.list());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','AUXILIAR','ALMACENISTA','CONTADOR')")
    public ResponseEntity<TaxResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(useCase.getById(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<TaxResponse> create(@Valid @RequestBody TaxRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(useCase.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<TaxResponse> update(@PathVariable UUID id,
                                               @Valid @RequestBody TaxRequest request) {
        return ResponseEntity.ok(useCase.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        useCase.delete(id);
        return ResponseEntity.noContent().build();
    }
}
