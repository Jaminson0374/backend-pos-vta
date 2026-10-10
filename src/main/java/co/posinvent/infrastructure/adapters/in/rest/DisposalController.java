package co.posinvent.infrastructure.adapters.in.rest;

import co.posinvent.application.dto.DisposalRequest;
import co.posinvent.application.dto.DisposalResponse;
import co.posinvent.application.usecase.CreateDisposalUseCase;
import co.posinvent.application.usecase.ListDisposalsUseCase;
import co.posinvent.application.usecase.ListExpiringBatchesUseCase;
import co.posinvent.infrastructure.adapters.out.security.PosUserDetails;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/disposals")
public class DisposalController {

    private final CreateDisposalUseCase createDisposal;
    private final ListDisposalsUseCase listDisposals;
    private final ListExpiringBatchesUseCase listExpiringBatches;

    public DisposalController(CreateDisposalUseCase createDisposal,
                              ListDisposalsUseCase listDisposals,
                              ListExpiringBatchesUseCase listExpiringBatches) {
        this.createDisposal = createDisposal;
        this.listDisposals = listDisposals;
        this.listExpiringBatches = listExpiringBatches;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','ALMACENISTA')")
    public ResponseEntity<DisposalResponse> create(@RequestBody DisposalRequest r,
                                                   @AuthenticationPrincipal PosUserDetails principal) {
        return ResponseEntity.ok(createDisposal.execute(r, principal.userId()));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','ALMACENISTA','CAJERO')")
    public ResponseEntity<Page<DisposalResponse>> list(
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) UUID productId,
            @RequestParam(required = false) String disposalType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to
    ) {
        return ResponseEntity.ok(listDisposals.execute(page, size));
    }

    @GetMapping("/expiring-soon")
    @PreAuthorize("hasAnyRole('ADMIN','ALMACENISTA','CAJERO')")
    public ResponseEntity<List<Map<String, Object>>> expiringSoon(
            @RequestParam(defaultValue = "30") int days
    ) {
        return ResponseEntity.ok(listExpiringBatches.execute(days));
    }
}
