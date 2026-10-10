package co.posinvent.infrastructure.adapters.in.rest;

import co.posinvent.application.dto.BatchRequest;
import co.posinvent.application.dto.BatchResponse;
import co.posinvent.application.dto.PageResponse;
import co.posinvent.application.port.in.BatchPort;
import co.posinvent.application.port.in.DisposeBatchPort;
import co.posinvent.domain.model.Batch.BatchStatus;
import co.posinvent.infrastructure.adapters.out.security.PosUserDetails;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/batches")
public class BatchController {

    private final BatchPort batchUseCase;
    private final DisposeBatchPort disposeBatchPort;

    public BatchController(BatchPort batchUseCase, DisposeBatchPort disposeBatchPort) {
        this.batchUseCase = batchUseCase;
        this.disposeBatchPort = disposeBatchPort;
    }

    @GetMapping
    public ResponseEntity<PageResponse<BatchResponse>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) BatchStatus status
    ) {
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "entryDate"));
        var result = status != null
                ? batchUseCase.listByStatus(status, pageable)
                : batchUseCase.list(pageable);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{id}")
    public ResponseEntity<BatchResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(batchUseCase.getById(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','CARNICERO','AUXILIAR')")
    public ResponseEntity<BatchResponse> create(
            @Valid @RequestBody BatchRequest request,
            @AuthenticationPrincipal PosUserDetails principal
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(batchUseCase.create(request, principal.userId()));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN','CARNICERO')")
    public ResponseEntity<BatchResponse> updateStatus(
            @PathVariable UUID id,
            @RequestParam BatchStatus status
    ) {
        return ResponseEntity.ok(batchUseCase.updateStatus(id, status));
    }

    @GetMapping("/{id}/children")
    @PreAuthorize("hasAnyRole('ADMIN','CARNICERO','AUXILIAR')")
    public ResponseEntity<List<BatchResponse>> listChildren(@PathVariable UUID id) {
        return ResponseEntity.ok(batchUseCase.listChildren(id));
    }

    @PostMapping("/{id}/dispose-expired")
    @PreAuthorize("hasAnyRole('ADMIN','CARNICERO')")
    public ResponseEntity<Map<String, Object>> disposeExpired(@PathVariable UUID id) {
        disposeBatchPort.disposeBatch(id);
        return ResponseEntity.ok(Map.of("message", "Lote dispuesto por vencimiento", "batchId", id));
    }
}
