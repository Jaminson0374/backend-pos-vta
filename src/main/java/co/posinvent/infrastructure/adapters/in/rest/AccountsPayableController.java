package co.posinvent.infrastructure.adapters.in.rest;

import co.posinvent.application.dto.AccountsPayableResponse;
import co.posinvent.application.dto.ApAgingResponse;
import co.posinvent.application.dto.PageResponse;
import co.posinvent.application.usecase.AccountsPayableUseCase;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/accounts-payable")
public class AccountsPayableController {

    private final AccountsPayableUseCase apUseCase;

    public AccountsPayableController(AccountsPayableUseCase apUseCase) {
        this.apUseCase = apUseCase;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','CONTADOR')")
    public ResponseEntity<PageResponse<AccountsPayableResponse>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) UUID supplierId,
            @RequestParam(required = false) String status
    ) {
        var result = apUseCase.list(page, size, supplierId, status);
        return ResponseEntity.ok(PageResponse.from(result, r -> r));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','CONTADOR')")
    public ResponseEntity<AccountsPayableResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(apUseCase.getById(id));
    }

    @GetMapping("/aging")
    @PreAuthorize("hasAnyRole('ADMIN','CONTADOR')")
    public ResponseEntity<ApAgingResponse> getAging(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf
    ) {
        return ResponseEntity.ok(apUseCase.getAging(asOf));
    }

    @PostMapping("/mark-overdue")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> markOverdue() {
        int count = apUseCase.markOverdue();
        return ResponseEntity.ok(Map.of(
                "message", "Cuentas por pagar marcadas como vencidas",
                "count", count
        ));
    }
}
