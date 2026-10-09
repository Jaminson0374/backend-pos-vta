package co.posinvent.infrastructure.adapters.in.rest;

import co.posinvent.application.dto.BatchRequest;
import co.posinvent.application.dto.BatchResponse;
import co.posinvent.application.dto.PageResponse;
import co.posinvent.application.port.in.BatchPort;
import co.posinvent.application.port.in.RecordMovementPort;
import co.posinvent.domain.exception.ResourceNotFoundException;
import co.posinvent.domain.model.Batch;
import co.posinvent.domain.model.Batch.BatchStatus;
import co.posinvent.domain.model.InventoryStock;
import co.posinvent.domain.model.MovementType;
import co.posinvent.domain.repository.BatchRepository;
import co.posinvent.domain.repository.StockRepository;
import co.posinvent.infrastructure.adapters.out.security.PosUserDetails;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/batches")
public class BatchController {

    private final BatchPort batchUseCase;
    private final BatchRepository batchRepository;
    private final StockRepository stockRepository;
    private final RecordMovementPort recordMovement;

    public BatchController(
            BatchPort batchUseCase,
            BatchRepository batchRepository,
            StockRepository stockRepository,
            RecordMovementPort recordMovement
    ) {
        this.batchUseCase = batchUseCase;
        this.batchRepository = batchRepository;
        this.stockRepository = stockRepository;
        this.recordMovement = recordMovement;
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
    public ResponseEntity<?> disposeExpired(@PathVariable UUID id) {
        // Find batch
        var batch = batchRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Lote", id));

        // Get stock
        var stocks = stockRepository.findByBatch(id);
        if (stocks.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "El lote no tiene stock registrado"));
        }

        // Dispose each stock entry
        for (var stock : stocks) {
            if (stock.currentQuantity().compareTo(BigDecimal.ZERO) > 0) {
                recordMovement.record(
                        stock.productId(), id, stock.warehouseId(),
                        MovementType.DISPOSAL,
                        stock.currentQuantity(), stock.unitCost(),
                        stock.currentQuantity(), BigDecimal.ZERO,
                        "EXPIRATION", id,
                        "Disposición manual por vencimiento — lote #" + id
                );
                stockRepository.save(new InventoryStock(
                        stock.id(), stock.productId(), stock.batchId(), stock.warehouseId(),
                        BigDecimal.ZERO, stock.committedQuantity(), stock.unitCost(),
                        stock.createdAt(), null
                ));
            }
        }

        // Close batch
        batchRepository.save(new Batch(
                batch.id(), batch.productId(), batch.supplierId(), batch.warehouseId(), batch.entryDate(),
                batch.initialWeight(), batch.purchaseCost(), BatchStatus.CLOSED,
                batch.notes(), batch.expirationDate(), batch.createdBy(),
                batch.createdAt(), null, batch.updatedBy(), batch.sourceReceiptId(), batch.ocId(),
                null, null, null,
                batch.parentBatchId(), batch.batchType(), batch.unitOfMeasureId()
        ));

        return ResponseEntity.ok(Map.of("message", "Lote dispuesto por vencimiento", "batchId", id));
    }
}
