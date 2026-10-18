package co.posinvent.infrastructure.adapters.in.rest;

import co.posinvent.application.usecase.CustomerStatementUseCase;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/customer-statements")
@PreAuthorize("hasAnyRole('ADMIN','CONTADOR','CAJERO')")
public class CustomerStatementController {

    private final CustomerStatementUseCase useCase;

    public CustomerStatementController(CustomerStatementUseCase useCase) {
        this.useCase = useCase;
    }

    @GetMapping("/{clientId}")
    public ResponseEntity<?> generate(
            @PathVariable UUID clientId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return ResponseEntity.ok(useCase.generate(clientId, from, to));
    }
}
