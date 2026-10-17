package co.posinvent.infrastructure.adapters.in.rest;

import co.posinvent.application.dto.PageResponse;
import co.posinvent.application.dto.SupplierInvoiceRequest;
import co.posinvent.application.dto.SupplierInvoiceResponse;
import co.posinvent.application.usecase.SupplierInvoiceUseCase;
import co.posinvent.domain.model.InvoiceStatus;
import co.posinvent.infrastructure.adapters.out.security.PosUserDetails;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/supplier-invoices")
public class SupplierInvoiceController {

    private final SupplierInvoiceUseCase supplierInvoiceUseCase;

    public SupplierInvoiceController(SupplierInvoiceUseCase supplierInvoiceUseCase) {
        this.supplierInvoiceUseCase = supplierInvoiceUseCase;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','AUXILIAR','CONTADOR')")
    public ResponseEntity<PageResponse<SupplierInvoiceResponse>> list(
            @RequestParam(required = false) UUID supplierId,
            @RequestParam(required = false) InvoiceStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "issueDate"));
        PageResponse<SupplierInvoiceResponse> result;

        if (supplierId != null && status != null) {
            result = supplierInvoiceUseCase.findBySupplierAndStatus(supplierId, status, pageable);
        } else if (supplierId != null) {
            result = supplierInvoiceUseCase.findBySupplier(supplierId, pageable);
        } else if (status != null) {
            result = supplierInvoiceUseCase.findByStatus(status, pageable);
        } else {
            result = supplierInvoiceUseCase.list(pageable);
        }

        return ResponseEntity.ok(result);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','AUXILIAR','CONTADOR')")
    public ResponseEntity<SupplierInvoiceResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(supplierInvoiceUseCase.getById(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','CONTADOR')")
    public ResponseEntity<SupplierInvoiceResponse> create(
            @Valid @RequestBody SupplierInvoiceRequest request,
            @AuthenticationPrincipal PosUserDetails principal
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(supplierInvoiceUseCase.create(request, principal.userId()));
    }

    @PatchMapping("/{id}/reconcile")
    @PreAuthorize("hasAnyRole('ADMIN','CONTADOR')")
    public ResponseEntity<SupplierInvoiceResponse> reconcile(@PathVariable UUID id) {
        return ResponseEntity.ok(supplierInvoiceUseCase.reconcile(id));
    }

    @PatchMapping("/{id}/dispute")
    @PreAuthorize("hasAnyRole('ADMIN','CONTADOR')")
    public ResponseEntity<SupplierInvoiceResponse> dispute(
            @PathVariable UUID id,
            @RequestBody Map<String, String> body
    ) {
        return ResponseEntity.ok(supplierInvoiceUseCase.dispute(id, body.get("reason")));
    }
}
