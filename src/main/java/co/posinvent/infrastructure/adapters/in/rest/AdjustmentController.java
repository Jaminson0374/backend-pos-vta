package co.posinvent.infrastructure.adapters.in.rest;

import co.posinvent.application.dto.AdjustmentRequest;
import co.posinvent.application.dto.AdjustmentResponse;
import co.posinvent.application.port.in.CreateAdjustmentPort;
import co.posinvent.application.port.in.ListAdjustmentsPort;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/adjustments")
public class AdjustmentController {

    private final CreateAdjustmentPort createAdjustment;
    private final ListAdjustmentsPort listAdjustments;

    public AdjustmentController(CreateAdjustmentPort createAdjustment, ListAdjustmentsPort listAdjustments) {
        this.createAdjustment = createAdjustment;
        this.listAdjustments = listAdjustments;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','ALMACENISTA')")
    public ResponseEntity<AdjustmentResponse> create(@RequestBody AdjustmentRequest request) {
        return ResponseEntity.ok(createAdjustment.execute(request));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','ALMACENISTA','CAJERO')")
    public ResponseEntity<Page<AdjustmentResponse>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) UUID productId,
            @RequestParam(required = false) UUID warehouseId,
            @RequestParam(required = false) String adjustmentType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to
    ) {
        var pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(listAdjustments.listFiltered(productId, warehouseId, adjustmentType, from, to, pageable));
    }
}
