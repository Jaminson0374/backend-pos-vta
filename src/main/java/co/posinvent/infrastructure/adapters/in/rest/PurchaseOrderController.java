package co.posinvent.infrastructure.adapters.in.rest;

import co.posinvent.application.dto.PageResponse;
import co.posinvent.application.dto.PurchaseOrderRequest;
import co.posinvent.application.dto.PurchaseOrderResponse;
import co.posinvent.application.usecase.PurchaseOrderUseCase;
import co.posinvent.domain.model.PurchaseOrderStatus;
import co.posinvent.infrastructure.adapters.out.security.PosUserDetails;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/purchase-orders")
public class PurchaseOrderController {

    private final PurchaseOrderUseCase purchaseOrderUseCase;

    public PurchaseOrderController(PurchaseOrderUseCase purchaseOrderUseCase) {
        this.purchaseOrderUseCase = purchaseOrderUseCase;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','AUXILIAR','CONTADOR')")
    public ResponseEntity<PageResponse<PurchaseOrderResponse>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) PurchaseOrderStatus status,
            @RequestParam(required = false) UUID supplierId,
            @RequestParam(required = false) String q
    ) {
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "orderDate"));

        if (q != null && !q.isBlank()) {
            return ResponseEntity.ok(purchaseOrderUseCase.search(q.trim(), pageable));
        }
        if (status != null) {
            return ResponseEntity.ok(purchaseOrderUseCase.findByStatus(status, pageable));
        }
        if (supplierId != null) {
            return ResponseEntity.ok(purchaseOrderUseCase.findBySupplier(supplierId, pageable));
        }
        return ResponseEntity.ok(purchaseOrderUseCase.list(pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','AUXILIAR','CONTADOR')")
    public ResponseEntity<PurchaseOrderResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(purchaseOrderUseCase.getById(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','AUXILIAR')")
    public ResponseEntity<PurchaseOrderResponse> create(
            @Valid @RequestBody PurchaseOrderRequest request,
            @AuthenticationPrincipal PosUserDetails principal
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(purchaseOrderUseCase.create(request, principal.userId()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','AUXILIAR')")
    public ResponseEntity<PurchaseOrderResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody PurchaseOrderRequest request
    ) {
        return ResponseEntity.ok(purchaseOrderUseCase.update(id, request));
    }

    @PatchMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('ADMIN','AUXILIAR')")
    public ResponseEntity<PurchaseOrderResponse> cancel(@PathVariable UUID id) {
        return ResponseEntity.ok(purchaseOrderUseCase.cancel(id));
    }
}
