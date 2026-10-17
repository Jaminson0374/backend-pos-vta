package co.posinvent.infrastructure.adapters.in.rest;

import co.posinvent.application.dto.DebitCreditNoteRequest;
import co.posinvent.application.dto.DebitCreditNoteResponse;
import co.posinvent.application.dto.PageResponse;
import co.posinvent.application.usecase.ManageDebitCreditNoteUseCase;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/debit-credit-notes")
public class DebitCreditNoteController {

    private final ManageDebitCreditNoteUseCase useCase;

    public DebitCreditNoteController(ManageDebitCreditNoteUseCase useCase) {
        this.useCase = useCase;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','AUXILIAR','CONTADOR')")
    public PageResponse<DebitCreditNoteResponse> list(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) UUID supplierId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return useCase.list(type, supplierId, page, size);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','AUXILIAR','CONTADOR')")
    public DebitCreditNoteResponse getById(@PathVariable UUID id) {
        return useCase.getById(id);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','CONTADOR')")
    @ResponseStatus(HttpStatus.CREATED)
    public DebitCreditNoteResponse create(@Valid @RequestBody DebitCreditNoteRequest request) {
        return useCase.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','CONTADOR')")
    public ResponseEntity<DebitCreditNoteResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody DebitCreditNoteRequest request
    ) {
        return ResponseEntity.ok(useCase.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','CONTADOR')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        useCase.delete(id);
        return ResponseEntity.noContent().build();
    }
}
