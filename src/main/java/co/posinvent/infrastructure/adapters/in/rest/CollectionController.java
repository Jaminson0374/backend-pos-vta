package co.posinvent.infrastructure.adapters.in.rest;

import co.posinvent.application.dto.LogContactRequest;
import co.posinvent.application.usecase.CollectionUseCase;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/collections")
@PreAuthorize("hasAnyRole('ADMIN','CONTADOR','CAJERO')")
public class CollectionController {

    private final CollectionUseCase useCase;

    public CollectionController(CollectionUseCase useCase) {
        this.useCase = useCase;
    }

    @GetMapping
    public ResponseEntity<?> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String clientId,
            @RequestParam(required = false) String status
    ) {
        return ResponseEntity.ok(useCase.list(PageRequest.of(page, size), clientId, status));
    }

    @GetMapping("/overdue")
    public ResponseEntity<?> listOverdue(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(useCase.listOverdue(PageRequest.of(page, size)));
    }

    @PostMapping("/{id}/contact")
    public ResponseEntity<?> logContact(@PathVariable UUID id, @RequestBody LogContactRequest request) {
        return ResponseEntity.ok(useCase.logContact(id, request));
    }
}
