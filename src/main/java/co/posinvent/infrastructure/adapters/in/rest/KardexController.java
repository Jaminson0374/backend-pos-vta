package co.posinvent.infrastructure.adapters.in.rest;

import co.posinvent.application.dto.InventoryMovementResponse;
import co.posinvent.application.port.in.KardexQueryPort;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/kardex")
public class KardexController {

    private final KardexQueryPort kardexQuery;

    public KardexController(KardexQueryPort kardexQuery) {
        this.kardexQuery = kardexQuery;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','ALMACENISTA','CAJERO')")
    public ResponseEntity<Page<InventoryMovementResponse>> search(
            @RequestParam(required = false) String productId,
            @RequestParam(required = false) String batchId,
            @RequestParam(required = false) String warehouseId,
            @RequestParam(required = false) String movementType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        var productUuid = parseUuid(productId);
        var batchUuid = parseUuid(batchId);
        var warehouseUuid = parseUuid(warehouseId);
        return ResponseEntity.ok(
                kardexQuery.search(productUuid, batchUuid, warehouseUuid, movementType, from, to, page, size)
        );
    }

    private static UUID parseUuid(String value) {
        if (value == null || value.isBlank()) return null;
        try { return UUID.fromString(value); }
        catch (IllegalArgumentException e) { return null; }
    }
}
