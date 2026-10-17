package co.posinvent.infrastructure.adapters.in.rest;

import co.posinvent.application.dto.CustomerBalanceResponse;
import co.posinvent.application.usecase.CustomerBalanceUseCase;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Read-only customer balance endpoint.
 *
 * <p>{@code GET /api/v1/clients/{id}/balance} returns the balance derived from the AR ledger,
 * where a negative {@code availableCredit} means the client is over the credit limit.</p>
 */
@RestController
@RequestMapping("/api/v1/clients")
public class CustomerBalanceController {

    private final CustomerBalanceUseCase customerBalanceUseCase;

    public CustomerBalanceController(CustomerBalanceUseCase customerBalanceUseCase) {
        this.customerBalanceUseCase = customerBalanceUseCase;
    }

    @GetMapping("/{id}/balance")
    @PreAuthorize("hasAnyRole('ADMIN','AUXILIAR','CONTADOR')")
    public ResponseEntity<CustomerBalanceResponse> getBalance(@PathVariable UUID id) {
        return ResponseEntity.ok(customerBalanceUseCase.getBalance(id));
    }
}
