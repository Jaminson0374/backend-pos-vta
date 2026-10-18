package co.posinvent.infrastructure.adapters.in.rest;

import co.posinvent.application.dto.*;
import co.posinvent.application.usecase.LogisticsUseCase;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/logistics")
public class LogisticsController {

    private final LogisticsUseCase logisticsUseCase;

    public LogisticsController(LogisticsUseCase logisticsUseCase) {
        this.logisticsUseCase = logisticsUseCase;
    }

    // ── Receipts ───────────────────────────────────────────────────

    @GetMapping("/receipts")
    @PreAuthorize("hasAnyRole('ADMIN','ALMACENISTA','AUXILIAR','CONTADOR')")
    public ResponseEntity<PageResponse<ReceiptResponse>> listReceipts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(logisticsUseCase.listReceipts(PageRequest.of(page, size)));
    }

    @GetMapping("/receipts/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','ALMACENISTA','AUXILIAR','CONTADOR')")
    public ResponseEntity<ReceiptResponse> getReceipt(@PathVariable UUID id) {
        return ResponseEntity.ok(logisticsUseCase.getReceipt(id));
    }

    @PostMapping("/receipts")
    @PreAuthorize("hasAnyRole('ADMIN','ALMACENISTA')")
    public ResponseEntity<ReceiptResponse> createReceipt(@Valid @RequestBody ReceiptRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(logisticsUseCase.createReceipt(request));
    }

    @PatchMapping("/receipts/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN','ALMACENISTA')")
    public ResponseEntity<ReceiptResponse> updateReceiptStatus(
            @PathVariable UUID id,
            @RequestParam String status) {
        return ResponseEntity.ok(logisticsUseCase.updateReceiptStatus(id, status));
    }

    // ── Pickings ───────────────────────────────────────────────────

    @GetMapping("/pickings")
    @PreAuthorize("hasAnyRole('ADMIN','ALMACENISTA','AUXILIAR','CONTADOR')")
    public ResponseEntity<PageResponse<PickingResponse>> listPickings(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(logisticsUseCase.listPickings(PageRequest.of(page, size)));
    }

    @GetMapping("/pickings/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','ALMACENISTA','AUXILIAR','CONTADOR')")
    public ResponseEntity<PickingResponse> getPicking(@PathVariable UUID id) {
        return ResponseEntity.ok(logisticsUseCase.getPicking(id));
    }

    @PostMapping("/pickings")
    @PreAuthorize("hasAnyRole('ADMIN','ALMACENISTA')")
    public ResponseEntity<PickingResponse> createPicking(@Valid @RequestBody PickingRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(logisticsUseCase.createPicking(request));
    }

    @PatchMapping("/pickings/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN','ALMACENISTA')")
    public ResponseEntity<PickingResponse> updatePickingStatus(
            @PathVariable UUID id,
            @RequestParam String status) {
        return ResponseEntity.ok(logisticsUseCase.updatePickingStatus(id, status));
    }

    // ── Shipments ──────────────────────────────────────────────────

    @GetMapping("/shipments")
    @PreAuthorize("hasAnyRole('ADMIN','ALMACENISTA','AUXILIAR','CONTADOR')")
    public ResponseEntity<PageResponse<ShipmentResponse>> listShipments(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(logisticsUseCase.listShipments(PageRequest.of(page, size)));
    }

    @GetMapping("/shipments/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','ALMACENISTA','AUXILIAR','CONTADOR')")
    public ResponseEntity<ShipmentResponse> getShipment(@PathVariable UUID id) {
        return ResponseEntity.ok(logisticsUseCase.getShipment(id));
    }

    @PostMapping("/shipments")
    @PreAuthorize("hasAnyRole('ADMIN','ALMACENISTA')")
    public ResponseEntity<ShipmentResponse> createShipment(@Valid @RequestBody ShipmentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(logisticsUseCase.createShipment(request));
    }

    @PatchMapping("/shipments/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN','ALMACENISTA')")
    public ResponseEntity<ShipmentResponse> updateShipmentStatus(
            @PathVariable UUID id,
            @RequestParam String status) {
        return ResponseEntity.ok(logisticsUseCase.updateShipmentStatus(id, status));
    }

    // ── Transport Guides ───────────────────────────────────────────

    @GetMapping("/transport-guides")
    @PreAuthorize("hasAnyRole('ADMIN','ALMACENISTA','AUXILIAR','CONTADOR')")
    public ResponseEntity<PageResponse<TransportGuideResponse>> listGuides(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(logisticsUseCase.listGuides(PageRequest.of(page, size)));
    }

    @GetMapping("/transport-guides/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','ALMACENISTA','AUXILIAR','CONTADOR')")
    public ResponseEntity<TransportGuideResponse> getGuide(@PathVariable UUID id) {
        return ResponseEntity.ok(logisticsUseCase.getGuide(id));
    }

    @PostMapping("/transport-guides")
    @PreAuthorize("hasAnyRole('ADMIN','ALMACENISTA')")
    public ResponseEntity<TransportGuideResponse> createGuide(@Valid @RequestBody TransportGuideRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(logisticsUseCase.createGuide(request));
    }

    @PatchMapping("/transport-guides/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN','ALMACENISTA')")
    public ResponseEntity<TransportGuideResponse> updateGuideStatus(
            @PathVariable UUID id,
            @RequestParam String status) {
        return ResponseEntity.ok(logisticsUseCase.updateGuideStatus(id, status));
    }
}
