package co.posinvent.infrastructure.adapters.in.rest;

import co.posinvent.application.dto.SupplierBalanceResponse;
import co.posinvent.application.usecase.SupplierBalanceUseCase;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Read-only supplier balance endpoint.
 *
 * <p>{@code GET /api/v1/suppliers/{id}/balance} returns the net position where unapplied advances
 * are an asset: a negative {@code netPosition} means we have credit in our favor.</p>
 */
@RestController
@RequestMapping("/api/v1/suppliers")
public class SupplierBalanceController {

    private final SupplierBalanceUseCase supplierBalanceUseCase;

    public SupplierBalanceController(SupplierBalanceUseCase supplierBalanceUseCase) {
        this.supplierBalanceUseCase = supplierBalanceUseCase;
    }

    @GetMapping("/{id}/balance")
    @PreAuthorize("hasAnyRole('ADMIN','AUXILIAR','CONTADOR')")
    public ResponseEntity<SupplierBalanceResponse> getBalance(@PathVariable UUID id) {
        return ResponseEntity.ok(supplierBalanceUseCase.getBalance(id));
    }
}
