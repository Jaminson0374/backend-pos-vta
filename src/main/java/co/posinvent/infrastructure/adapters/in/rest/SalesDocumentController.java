package co.posinvent.infrastructure.adapters.in.rest;

import co.posinvent.application.dto.*;
import co.posinvent.application.usecase.ManageSalesDocumentUseCase;
import co.posinvent.domain.exception.ResourceNotFoundException;
import co.posinvent.domain.model.SalesDocumentStatus;
import co.posinvent.domain.model.SalesDocumentType;
import co.posinvent.infrastructure.adapters.out.security.PosUserDetails;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/sales")
public class SalesDocumentController {

    private final ManageSalesDocumentUseCase useCase;

    public SalesDocumentController(ManageSalesDocumentUseCase useCase) {
        this.useCase = useCase;
    }

    @PostMapping("/documents")
    @PreAuthorize("hasAnyRole('ADMIN','CAJERO')")
    public ResponseEntity<SalesDocumentResponse> create(
            @Valid @RequestBody SalesDocumentRequest request,
            @AuthenticationPrincipal PosUserDetails principal
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(useCase.createDocument(request, principal.userId()));
    }

    @PostMapping("/documents/{id}/transition")
    @PreAuthorize("hasAnyRole('ADMIN','CAJERO')")
    public ResponseEntity<SalesDocumentResponse> transition(
            @PathVariable UUID id,
            @Valid @RequestBody TransitionRequest request
    ) {
        return ResponseEntity.ok(useCase.transitionDocument(id, request.targetStatus()));
    }

    @GetMapping("/documents")
    @PreAuthorize("hasAnyRole('ADMIN','CAJERO','CONTADOR')")
    public ResponseEntity<PageResponse<SalesDocumentResponse>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) SalesDocumentType type,
            @RequestParam(required = false) SalesDocumentStatus status
    ) {
        return ResponseEntity.ok(useCase.getDocuments(page, size, type, status));
    }

    @GetMapping("/documents/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','CAJERO','CONTADOR')")
    public ResponseEntity<SalesDocumentResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(useCase.getById(id));
    }

    @PostMapping("/documents/{id}/items")
    @PreAuthorize("hasAnyRole('ADMIN','CAJERO')")
    public ResponseEntity<SalesDocumentResponse> addItem(
            @PathVariable UUID id,
            @Valid @RequestBody SaleItemRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(useCase.addItem(id, request));
    }

    @PutMapping("/items/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','CAJERO')")
    public ResponseEntity<SalesDocumentResponse> updateItem(
            @PathVariable UUID id,
            @Valid @RequestBody SaleItemRequest request
    ) {
        return ResponseEntity.ok(useCase.updateItem(id, request));
    }

    @DeleteMapping("/items/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','CAJERO')")
    public ResponseEntity<Void> removeItem(@PathVariable UUID id) {
        useCase.removeItem(id);
        return ResponseEntity.noContent().build();
    }
}
